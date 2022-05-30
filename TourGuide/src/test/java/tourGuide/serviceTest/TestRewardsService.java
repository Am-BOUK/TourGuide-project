package tourGuide;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

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

@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
public class TestRewardsService {
	private RewardsService rewardsService;
//	@Mock
	private GpsUtil gpsUtil;

	@Before
	public void setUpTest() {
		Locale.setDefault(Locale.US);
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
		List<UserReward> userRewards = user.getUserRewards();
		tourGuideService.tracker.stopTracking();
		assertTrue(userRewards.size() == 1);
	}

	@Test
	public void isWithinAttractionProximityTest() {
		Attraction attraction = gpsUtil.getAttractions().get(0);
		assertTrue(rewardsService.isWithinAttractionProximity(attraction, attraction));
	}

	@Test //Needs fixed
	public void nearAllAttractionsTest() {
		rewardsService.setProximityBuffer(Integer.MAX_VALUE);

		InternalTestHelper.setInternalUserNumber(1);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		User user = tourGuideService.getAllUsers().get(0);
		rewardsService.calculateRewards(user);
		List<UserReward> userRewards = tourGuideService.getUserRewards(user);

		tourGuideService.tracker.stopTracking();

		assertEquals(gpsUtil.getAttractions().size(), userRewards.size());
//		assertNotNull(userRewards);

	}

	@Test
	public void getDistanceTest() {
		double result = rewardsService.getDistance(new Location(10, 20), new Location(30, 40));
		Double distance = 1888.0699025936347;
		assertTrue(distance.equals(result));
	}
}
