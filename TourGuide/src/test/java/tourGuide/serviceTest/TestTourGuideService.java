package tourGuide.serviceTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.validation.constraints.AssertTrue;

import org.junit.Before;
import org.junit.Test;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import tourGuide.constants.NearByAttractions;
import tourGuide.exceptions.UserNameNotFoundException;
import tourGuide.exceptions.UserPreferencesException;
import tourGuide.helper.InternalTestHelper;
import tourGuide.service.RewardsService;
import tourGuide.service.TourGuideService;
import tourGuide.user.User;
import tourGuide.user.UserPreferences;
import tourGuide.user.UserReward;
import tripPricer.Provider;

public class TestTourGuideService {
	private TourGuideService tourGuideService;
	private GpsUtil gpsUtil;
	private User user;

	@Before
	public void setUpTest() {
		Locale.setDefault(Locale.US);
		gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());
		InternalTestHelper.setInternalUserNumber(0);
		tourGuideService = new TourGuideService(gpsUtil, rewardsService);
		user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");
	}

	@Test
	public void getUserLocationTest() {
		VisitedLocation visitedLocation = tourGuideService.getUserLocation(user);
		tourGuideService.tracker.stopTracking();
		ThreadPoolExecutor executor = (ThreadPoolExecutor) tourGuideService.getExecutor();
		while (executor.getActiveCount() > 0) {
			try {
				TimeUnit.SECONDS.sleep(5);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		assertTrue(visitedLocation.userId.equals(user.getUserId()));
	}

	@Test
	public void getUserRewardsTest() {
		List<Attraction> attractions = gpsUtil.getAttractions();
		attractions.forEach(attraction -> user
				.addToVisitedLocations(new VisitedLocation(user.getUserId(), attraction, new Date())));
		tourGuideService.trackUserLocation(user);
		ThreadPoolExecutor executor = (ThreadPoolExecutor) tourGuideService.getExecutor();
		while (executor.getActiveCount() > 0) {
			try {
				TimeUnit.SECONDS.sleep(15);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		List<UserReward> getUserRewards = tourGuideService.getUserRewards(user);
		assertEquals(attractions.size(), getUserRewards.size());
	}

	@Test
	public void getUserTest() throws UserNameNotFoundException {
		tourGuideService.addUser(user);
		User retrivedUser = tourGuideService.getUser(user.getUserName());
		tourGuideService.tracker.stopTracking();

		assertEquals(user, retrivedUser);
	}

	@Test
	public void getUserTest_WhenUserDoesNotExist_ShouldRerurnUserNameNotFoundException() {
		try {
			tourGuideService.getUser("toto");
		} catch (UserNameNotFoundException e) {
			assertTrue(e instanceof UserNameNotFoundException);
			assertTrue(e.getMessage().contains("The userName : toto, does not exist !"));
		}
		tourGuideService.tracker.stopTracking();
	}

	@Test
	public void addUserTest() throws UserNameNotFoundException {
		User user2 = new User(UUID.randomUUID(), "jon2", "000", "jon2@tourGuide.com");

		tourGuideService.addUser(user);
		tourGuideService.addUser(user2);

		User retrivedUser = tourGuideService.getUser(user.getUserName());
		User retrivedUser2 = tourGuideService.getUser(user2.getUserName());

		tourGuideService.tracker.stopTracking();

		assertEquals(user, retrivedUser);
		assertEquals(user2, retrivedUser2);
	}

	@Test
	public void getAllUsersTest() {
		User user2 = new User(UUID.randomUUID(), "jon2", "000", "jon2@tourGuide.com");

		tourGuideService.addUser(user);
		tourGuideService.addUser(user2);

		List<User> allUsers = tourGuideService.getAllUsers();

		tourGuideService.tracker.stopTracking();

		assertTrue(allUsers.contains(user));
		assertTrue(allUsers.contains(user2));
	}

	@Test
	public void trackUserLocationTest() {
		tourGuideService.trackUserLocation(user);
		ThreadPoolExecutor executor = (ThreadPoolExecutor) tourGuideService.getExecutor();
		while (executor.getActiveCount() > 0) {
			try {
				TimeUnit.SECONDS.sleep(5);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		tourGuideService.tracker.stopTracking();
		assertEquals(user.getUserId(), user.getLastVisitedLocation().userId);
	}

	@Test
	public void getNearByAttractionsTest() {
		VisitedLocation visitedLocation = tourGuideService.getUserLocation(user);

		ThreadPoolExecutor executor = (ThreadPoolExecutor) tourGuideService.getExecutor();
		while (executor.getActiveCount() > 0) {
			try {
				TimeUnit.SECONDS.sleep(5);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		Map<String, Map<String, Double>> attractions = tourGuideService.getNearByAttractions(visitedLocation);
		tourGuideService.tracker.stopTracking();

		assertEquals(NearByAttractions.CLOSEST_ATTRACTIONS_NUMBER, attractions.size());
	}

	@Test
	public void getTripDealsTest() {
		List<Provider> providers = tourGuideService.getTripDeals(user);
		tourGuideService.tracker.stopTracking();

		assertEquals(5, providers.size());
	}

	@Test
	public void getAllCurrentLocationsTest() {
		User user2 = new User(UUID.randomUUID(), "jon2", "000", "jon2@tourGuide.com");
		tourGuideService.addUser(user);
		tourGuideService.addUser(user2);

		List<User> allUsers = tourGuideService.getAllUsers();

		Map<String, Location> getAllCurrentLocations = tourGuideService.getAllCurrentLocations();
		ThreadPoolExecutor executor = (ThreadPoolExecutor) tourGuideService.getExecutor();
		while (executor.getActiveCount() > 0) {
			try {
				TimeUnit.SECONDS.sleep(5);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		tourGuideService.tracker.stopTracking();
		assertEquals(allUsers.size(), getAllCurrentLocations.size());
	}

	@Test
	public void updateUserTest() throws UserNameNotFoundException, UserPreferencesException {
		tourGuideService.addUser(user);
		UserPreferences userPreferences = user.getUserPreferences();
		userPreferences.setNumberOfChildren(3);

		UserPreferences result = tourGuideService.updateUserPreferences(user.getUserName(), userPreferences);

		assertEquals(3, result.getNumberOfChildren());
	}

	@Test
	public void updateUserTest_whenUserDoesNotExist_shouldReturnUserNameNotFoundException() {
		tourGuideService.addUser(user);
		UserPreferences userPreferences = user.getUserPreferences();
		userPreferences.setNumberOfChildren(3);
		try {
			tourGuideService.updateUserPreferences("toto", userPreferences);
		} catch (UserNameNotFoundException | UserPreferencesException e) {
			assertTrue(e instanceof UserNameNotFoundException);
			assertThat(e.getMessage()).contains("The userName : toto, does not exist !");
		}
	}

	@Test
	public void updateUserTest_whenNumberOfAdultsNull_shouldReturnUserNameNotFoundException() {
		tourGuideService.addUser(user);
		UserPreferences userPreferences = user.getUserPreferences();
		userPreferences.setNumberOfAdults(0);
		try {
			tourGuideService.updateUserPreferences(user.getUserName(), userPreferences);
		} catch (UserNameNotFoundException | UserPreferencesException e) {
			assertTrue(e instanceof UserPreferencesException);
			assertThat(e.getMessage()).contains("Number Of Adults can not be null !");
		}
	}

	@Test
	public void updateUserTest_whenTicketQuantityNull_shouldReturnUserNameNotFoundException() {
		tourGuideService.addUser(user);
		UserPreferences userPreferences = user.getUserPreferences();
		userPreferences.setTicketQuantity(0);
		try {
			tourGuideService.updateUserPreferences(user.getUserName(), userPreferences);
		} catch (UserNameNotFoundException | UserPreferencesException e) {
			assertTrue(e instanceof UserPreferencesException);
			assertThat(e.getMessage()).contains("Ticket Quantity can not be null !");
		}
	}

	@Test
	public void updateUserTest_whenTripDurationNull_shouldReturnUserNameNotFoundException() {
		tourGuideService.addUser(user);
		UserPreferences userPreferences = user.getUserPreferences();
		userPreferences.setTripDuration(0);
		try {
			tourGuideService.updateUserPreferences(user.getUserName(), userPreferences);
		} catch (UserNameNotFoundException | UserPreferencesException e) {
			assertTrue(e instanceof UserPreferencesException);
			assertThat(e.getMessage()).contains("Trip Duration can not be null !");
		}
	}
	
}
