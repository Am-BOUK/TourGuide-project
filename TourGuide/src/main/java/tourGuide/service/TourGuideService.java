package tourGuide.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import tourGuide.helper.InternalTestHelper;
import tourGuide.tracker.Tracker;
import tourGuide.user.User;
import tourGuide.user.UserReward;
import tripPricer.Provider;
import tripPricer.TripPricer;

@Service
public class TourGuideService {
	private Logger logger = LoggerFactory.getLogger(TourGuideService.class);
	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	boolean testMode = true;

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

	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	public VisitedLocation getUserLocation(User user) {
		VisitedLocation visitedLocation = (user.getVisitedLocations().size() > 0) ? user.getLastVisitedLocation()
				: trackUserLocation(user);
		return visitedLocation;
	}

	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}

	public List<User> getAllUsers() {
		return internalUserMap.values().stream().collect(Collectors.toList());
	}

	public void addUser(User user) {
		if (!internalUserMap.containsKey(user.getUserName())) {
			internalUserMap.put(user.getUserName(), user);
		}
	}

	public List<Provider> getTripDeals(User user) {
		int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();
		List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
				user.getUserPreferences().getNumberOfAdults(), user.getUserPreferences().getNumberOfChildren(),
				user.getUserPreferences().getTripDuration(), cumulatativeRewardPoints);
		user.setTripDeals(providers);
		return providers;
	}

	public VisitedLocation trackUserLocation(User user) {
		VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
		user.addToVisitedLocations(visitedLocation);
		rewardsService.calculateRewards(user);
		return visitedLocation;
	}

	/**
	 * Get a list of every user's most recent location
	 * 
	 * @return List : mapping of userId to Locations
	 */
	public Map<String, Location> getAllCurrentLocations() {
		Map<String, Location> usersLocationMap = new HashMap<>();
		for (User user : getAllUsers()) {
			String userInfo = user.getUserId().toString();
			VisitedLocation recentLocation = getUserLocation(user);
			usersLocationMap.put(userInfo, recentLocation.location);
		}
		return usersLocationMap;
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
		User user = getUserByUUID(visitedLocation.userId);
		logger.info("Get the closest five tourist attractions to the user : " + user.getUserName());

		Double distance = 0.0;

		Map<Double, Attraction> mapNearbyAttractions = new HashMap<>();

		// calculate the distance between the user's location and each of the
		// attractions, then fill in our map
		for (Attraction attraction : gpsUtil.getAttractions()) {
			distance = rewardsService.getDistance(attraction, visitedLocation.location);
			mapNearbyAttractions.put(distance, attraction);
		}

		// sort attractions in descending order of distances
		TreeMap<Double, Attraction> treeMapDistanceWithAttraction = new TreeMap<>(mapNearbyAttractions);

		// retrieve ordered keys
		Set<Double> distanceSortedList = treeMapDistanceWithAttraction.keySet();
		Iterator<Double> iteratorDistance = distanceSortedList.iterator();
		Map<Double, Attraction> nearbyAttractionsList = new HashMap<>();
		Map<String, Map<String, Double>> nearbyAttractionsMap = new HashMap<>();
		Attraction sortedAttraction;
		int i = 0;
		while (i < 5 && iteratorDistance.hasNext()) {
			Double distanceAttraction = iteratorDistance.next();
			System.out.println(
					distanceAttraction + ":" + treeMapDistanceWithAttraction.get(distanceAttraction).attractionName);
			sortedAttraction = treeMapDistanceWithAttraction.get(distanceAttraction);
			nearbyAttractionsList.put(distanceAttraction, sortedAttraction);

			String attractionName = "Attraction name : "
					+ treeMapDistanceWithAttraction.get(distanceAttraction).attractionName;
			Map<String, Double> nearbyAttractionsMapList = new HashMap<String, Double>();

			nearbyAttractionsMapList.put("Attraction latitude ",
					treeMapDistanceWithAttraction.get(distanceAttraction).latitude);
			nearbyAttractionsMapList.put("Attraction longitude ",
					treeMapDistanceWithAttraction.get(distanceAttraction).longitude);
			nearbyAttractionsMapList.put("User latitude ", visitedLocation.location.latitude);
			nearbyAttractionsMapList.put("User longitude ", visitedLocation.location.longitude);
			nearbyAttractionsMapList.put("Distance User_Attraction in miles", rewardsService
					.getDistance(visitedLocation.location, treeMapDistanceWithAttraction.get(distanceAttraction)));
			nearbyAttractionsMapList.put("Reward points", Double.valueOf(
					rewardsService.getRewardPoints(treeMapDistanceWithAttraction.get(distanceAttraction), user)));

			nearbyAttractionsMap.put(attractionName, nearbyAttractionsMapList);
			i++;
		}

		return nearbyAttractionsMap;
	}

	/**
	 * Get User Object by his UUID
	 * 
	 * @param UUID : the userId
	 * @return Object : user
	 */
	public User getUserByUUID(UUID userId) {
		return internalUserMap.values().stream().filter(user -> user.getUserId().equals(userId)).findFirst().get();
	}

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
	private static final String tripPricerApiKey = "test-server-api-key";
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
		IntStream.range(0, 3).forEach(i -> {
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

}
