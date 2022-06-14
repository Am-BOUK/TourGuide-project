package tourGuide.serviceTest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import tourGuide.helper.InternalTestHelper;
import tourGuide.service.RewardsService;
import tourGuide.service.TourGuideService;
import tourGuide.user.User;
import tourGuide.user.UserReward;

public class TestRewardsService {
	private GpsUtil gpsUtil;
	private RewardsService rewardsService;

	@Before
	public void setUpTest() {
		gpsUtil = new GpsUtil();
		rewardsService = new RewardsService(gpsUtil, new RewardCentral());
	}

	@Test
	public void calculateRewardsTest() {
		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");
		Attraction attraction = gpsUtil.getAttractions().get(0);
		VisitedLocation visitedLocation = new VisitedLocation(user.getUserId(), attraction, new Date());
		user.addToVisitedLocations(visitedLocation);

		rewardsService.calculateRewards(user);
		ThreadPoolExecutor executor = (ThreadPoolExecutor) rewardsService.getExecutor();
		while (executor.getActiveCount() > 0) {
			try {
				TimeUnit.SECONDS.sleep(1);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		assertEquals(1, user.getUserRewards().size());
	}

	@Test
	public void userGetRewardsTest() {
		InternalTestHelper.setInternalUserNumber(0);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");
		Attraction attraction = gpsUtil.getAttractions().get(0);
		user.addToVisitedLocations(new VisitedLocation(user.getUserId(), attraction, new Date()));
		tourGuideService.trackUserLocation(user);

		ThreadPoolExecutor executor = (ThreadPoolExecutor) tourGuideService.getExecutor();
		while (executor.getActiveCount() > 0) {
			try {
				TimeUnit.SECONDS.sleep(5);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		List<UserReward> userRewards = user.getUserRewards();
		tourGuideService.tracker.stopTracking();
		assertTrue(userRewards.size() == 1);
	}

	@Test
	public void isWithinAttractionProximityTest() {
		Attraction attraction = gpsUtil.getAttractions().get(0);
		assertTrue(rewardsService.isWithinAttractionProximity(attraction, attraction));
	}

	@Test
	public void nearAllAttractionsTest() {
		rewardsService.setProximityBuffer(Integer.MAX_VALUE);
		InternalTestHelper.setInternalUserNumber(1);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);
		User user = tourGuideService.getAllUsers().get(0);

		rewardsService.calculateRewards(user);
		ThreadPoolExecutor executorService = (ThreadPoolExecutor) rewardsService.getExecutor();
		while (executorService.getActiveCount() > 0) {
			try {
				TimeUnit.SECONDS.sleep(1);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		List<UserReward> userRewards = tourGuideService.getUserRewards(tourGuideService.getAllUsers().get(0));
		tourGuideService.tracker.stopTracking();

		assertEquals(gpsUtil.getAttractions().size(), userRewards.size());
	}

	@Ignore
	@Test
	public void getDistanceTest() {
		Locale.setDefault(Locale.US);
		double result = rewardsService.getDistance(new Location(10, 20), new Location(30, 40));
		Double distance = 1888.0699025936347;
		assertTrue(distance.equals(result));
	}

}
