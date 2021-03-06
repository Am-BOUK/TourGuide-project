package tourGuide.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import tourGuide.constants.GenerateUserLocationHistory;
import tourGuide.constants.InternalDefaultValue;
import tourGuide.constants.NearByAttractions;
import tourGuide.exceptions.UserNameNotFoundException;
import tourGuide.exceptions.UserPreferencesException;
import tourGuide.helper.InternalTestHelper;
import tourGuide.tracker.Tracker;
import tourGuide.user.User;
import tourGuide.user.UserPreferences;
import tourGuide.user.UserReward;
import tripPricer.Provider;
import tripPricer.TripPricer;

@Service
public class TourGuideService {
	private Logger logger = LoggerFactory.getLogger(TourGuideService.class);
	// service d'ex?cuteur de type pool de threads fix
	private final ExecutorService executor = Executors.newFixedThreadPool(1000);
	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	private final RewardCentral rewardsCentral = new RewardCentral();
	boolean testMode = true;

	/**
	 * configuration service test
	 * 
	 * @param gpsUtil
	 * @param rewardsService
	 */
	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;

		if (testMode) {
			logger.info("TestMode enabled");
			logger.debug("Initializing users");
			initializeInternalUsers();
			logger.debug("Finished initializing users");
		}
		tracker = new Tracker(this);
		addShutDownHook();
	}

	/**
	 * Get list of rewards of the giving user
	 * 
	 * @param user : the user Object
	 * @return list : the list of user's rewards
	 * @throws UserNameNotFoundException
	 */
	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	/**
	 * Get last visited location of the giving user
	 * 
	 * @param user : the user object
	 * @return VisitedLocation : the last visited location or track location of the
	 *         user
	 * @throws UserNameNotFoundException
	 */
	public VisitedLocation getUserLocation(User user) {
		logger.info("Get last visited location of the user : " + user.getUserName());
		if (user.getVisitedLocations().size() > 0) {
			return user.getLastVisitedLocation();
		} else {
			trackUserLocation(user);
			return gpsUtil.getUserLocation(user.getUserId());
		}
	}

	/**
	 * Get user object by userName
	 * 
	 * @param userName : the user name of the user we want to get
	 * @return User : the user object
	 * @throws UserNameNotFoundException
	 */
	public User getUser(String userName) throws UserNameNotFoundException {
		if (!checkIfUserNameExists(userName)) {
			logger.error("The userName : " + userName + ", does not exist !");
			throw new UserNameNotFoundException(" The userName : " + userName + ", does not exist !");
		}
		logger.info("Get user object of the username : " + userName);
		return internalUserMap.get(userName);
	}

	public ExecutorService getExecutor() {
		return executor;
	}

	/**
	 * get list of all users
	 * 
	 * @return List : the users list
	 */
	public List<User> getAllUsers() {
		logger.info("Get list of all users");
		return internalUserMap.values().stream().collect(Collectors.toList());
	}

	/**
	 * Add a new user ** This operation allows to check if the userName of the user
	 * we want to add already exists in the database, then allows to add it
	 * 
	 *
	 * @param user : user object to add
	 */
	public void addUser(User user) {
		logger.info("Add a new user : " + user.getUserName());
		if (!internalUserMap.containsKey(user.getUserName())) {
			internalUserMap.put(user.getUserName(), user);
		}
	}

	/**
	 * get trip deals of the giving user. *** this method first allows to accumulate
	 * the reward points earned by the giving user, then allows to obtain a list of
	 * travel offers / trip deals based on the price of the trip, the user ID, the
	 * number of adults, the number of children, the duration of the trip and the
	 * cumulative reward points that have already been calculated
	 * 
	 * @param user : the user object we want to get his trip deals list.
	 * @return List : trip deals list of the user.
	 */
	public List<Provider> getTripDeals(User user) {
		logger.info("Get trip deals of the user : " + user.getUserName());
		int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();
		List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
				user.getUserPreferences().getNumberOfAdults(), user.getUserPreferences().getNumberOfChildren(),
				user.getUserPreferences().getTripDuration(), cumulatativeRewardPoints);
		user.setTripDeals(providers);
		return providers;
	}

	/**
	 * track the location of a given user
	 * 
	 * @param user
	 */
	public void trackUserLocation(User user) {
		Locale.setDefault(Locale.US);
		CompletableFuture.supplyAsync(() -> {
			VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
			user.addToVisitedLocations(visitedLocation);
			return visitedLocation;
		}, executor).thenAccept(location -> {
			rewardsService.calculateRewards(user);
		}).exceptionally(throwable -> {
			throw new RejectedExecutionException(throwable.getMessage());
		});
	}

	/**
	 * Get the closest five tourist attractions to the user *** this method allows
	 * to return a list of closest five attractions to the giving user that
	 * contains: Name of Tourist attraction, Tourist attractions lat/long, The
	 * user's location lat/long, The distance in miles between the user's location
	 * and each of the attractions and the reward points for visiting each
	 * Attraction (attraction reward points will be gathered from RewardsCentral)
	 * 
	 * @param visitedLocation : the VisitedLocation object.
	 * @return Map : the closest attraction list to the user.
	 */
	public Map<String, Map<String, Double>> getNearByAttractions(VisitedLocation visitedLocation) {
		Double distance = 0.0;
		Map<Double, Attraction> mapAttractionsByDistance = new HashMap<>();
		Map<String, Map<String, Double>> nearbyAttractions = new HashMap<>();

		// calculate the distance between the user's location and each of the
		// attractions, then fill in our map
		for (Attraction attraction : gpsUtil.getAttractions()) {
			distance = rewardsService.getDistance(attraction, visitedLocation.location);
			mapAttractionsByDistance.put(distance, attraction);
		}

		// sort attractions in descending order by distances
		TreeMap<Double, Attraction> treeMapAttractionSodtedByDistance = new TreeMap<>(mapAttractionsByDistance);

		// retrieve ordered keys
		Set<Double> orderedDistancesKeys = treeMapAttractionSodtedByDistance.keySet();
		Iterator<Double> iteratorOfOrderedDistancesKeys = orderedDistancesKeys.iterator();

		int i = 0;
		while (i < NearByAttractions.CLOSEST_ATTRACTIONS_NUMBER && iteratorOfOrderedDistancesKeys.hasNext()) {
			Double distanceAttraction = iteratorOfOrderedDistancesKeys.next();
			System.out.println(distanceAttraction + ":"
					+ treeMapAttractionSodtedByDistance.get(distanceAttraction).attractionName);
			Attraction attraction = treeMapAttractionSodtedByDistance.get(distanceAttraction);

			String attractionName = "Attraction name : " + attraction.attractionName;
			Map<String, Double> nearbyAttractionsMapList = new HashMap<String, Double>();

			nearbyAttractionsMapList.put("Attraction latitude ", attraction.latitude);
			nearbyAttractionsMapList.put("Attraction longitude ", attraction.longitude);
			nearbyAttractionsMapList.put("User latitude ", visitedLocation.location.latitude);
			nearbyAttractionsMapList.put("User longitude ", visitedLocation.location.longitude);
			nearbyAttractionsMapList.put("Distance User_Attraction in miles", distanceAttraction);
			nearbyAttractionsMapList.put("Reward points", Double.valueOf(
					rewardsCentral.getAttractionRewardPoints(attraction.attractionId, visitedLocation.userId)));

			nearbyAttractions.put(attractionName, nearbyAttractionsMapList);
			i++;
		}
		return nearbyAttractions;
	}

	/**
	 * Get a list of every user's most recent location
	 * 
	 * @return List : mapping of userId to Locations
	 * @throws UserNameNotFoundException
	 */
	public Map<String, Location> getAllCurrentLocations() {
		logger.info("Get a list of every user's most recent location");
		Map<String, Location> usersLocationMap = new HashMap<>();
		for (User user : getAllUsers()) {
			String userInfo = user.getUserId().toString();
			VisitedLocation recentLocation = getUserLocation(user);
			usersLocationMap.put(userInfo, recentLocation.location);
		}
		return usersLocationMap;
	}

	/**
	 * Sets the user preferences with the new user preferences
	 * 
	 * @param userName
	 * @param userPreferences
	 * @return
	 * @throws UserNameNotFoundException
	 * @throws UserPreferencesException
	 */
	public UserPreferences updateUserPreferences(String userName, UserPreferences userPreferences)
			throws UserNameNotFoundException, UserPreferencesException {
		User user = getUser(userName);
		if (userPreferences.getNumberOfAdults() == 0) {
			throw new UserPreferencesException("Number Of Adults can not be null !");
		}
		if (userPreferences.getTripDuration() == 0) {
			throw new UserPreferencesException("Trip Duration can not be null !");
		}
		if (userPreferences.getTicketQuantity() == 0) {
			throw new UserPreferencesException("Ticket Quantity can not be null !");
		}
		user.setUserPreferences(userPreferences);

		return user.getUserPreferences();
	}

	/**
	 * Add a shut down hook for stopping the Tracker thread before shutting down the
	 * JVM
	 */
	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				tracker.stopTracking();
			}
		});
	}

	/**********************************************************************************
	 *
	 * Methods Below: For Internal Testing
	 *
	 **********************************************************************************/
	private static final String tripPricerApiKey = InternalDefaultValue.TRIP_PRICER_API_KEY;
	// Database connection will be used for external users, but for testing purposes
	// internal users are provided and stored in memory
	private final Map<String, User> internalUserMap = new HashMap<>();

	private void initializeInternalUsers() {
		IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
			String userName = "internalUser" + i;
			String phone = "000";
			String email = userName + "@tourGuide.com";
			User user = new User(UUID.randomUUID(), userName, phone, email);
			generateUserLocationHistory(user);

			internalUserMap.put(userName, user);
		});
		logger.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
	}

	private void generateUserLocationHistory(User user) {
		IntStream.range(0, GenerateUserLocationHistory.DEFAULT_VALUE).forEach(i -> {
			user.addToVisitedLocations(new VisitedLocation(user.getUserId(),
					new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()));
		});
	}

	private double generateRandomLongitude() {
		double leftLimit = -180;
		double rightLimit = 180;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private double generateRandomLatitude() {
		double leftLimit = -85.05112878;
		double rightLimit = 85.05112878;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private Date getRandomTime() {
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}

	/**
	 * Check if the InternalUserMap contains already the userName
	 *
	 * @param userName the string of the username
	 * @return the boolean of the check
	 */
	public boolean checkIfUserNameExists(String userName) {
		return internalUserMap.containsKey(userName) ? true : false;
	}

}
