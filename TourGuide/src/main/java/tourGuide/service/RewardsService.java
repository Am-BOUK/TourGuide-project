package tourGuide.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import tourGuide.user.User;
import tourGuide.user.UserReward;

@Service
public class RewardsService {
	private Logger logger = LoggerFactory.getLogger(RewardsService.class);
	private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

	// proximity in miles
	private int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;
	private int attractionProximityRange = 200;
	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;

	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
	}

	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}

	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}

	/**
	 * Add user reward of the giving user. *** this operation allows to Add the
	 * reward of the giving user if an attraction is visited.
	 * 
	 * @param user : the user object
	 */
	public void calculateRewards(User user) {
		logger.info("Add user reward of the user : " + user.getUserName());
		List<VisitedLocation> userLocations = user.getVisitedLocations();
		List<Attraction> attractions = gpsUtil.getAttractions();

		for (int i = 0; i < userLocations.size(); i++) {
			VisitedLocation visitedLocation = userLocations.get(i);
			for (int j = 0; j < attractions.size(); j++) {
				Attraction attraction = attractions.get(j);
				if (user.getUserRewards().stream()
						.filter(r -> r.attraction.attractionName.equals(attraction.attractionName)).count() == 0) {
					if (nearAttraction(visitedLocation, attraction)) {
						user.addUserReward(
								new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user)));
					}
				}
			}
		}
	}

	/**
	 * Check if the attraction is near to the location to be visited.
	 * 
	 * @param attraction : the attraction object
	 * @param location   : the location object of the user
	 * @return boolean : true if the distance is close false otherwise.
	 */
	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		logger.info(" Check if the attraction is near to the location ");
		return getDistance(attraction, location) > attractionProximityRange ? false : true;
	}

	/**
	 * Check if the visited location is near to the attraction to be visited.
	 * 
	 * @param visitedLocation : the visitedLocation object
	 * @param attraction      : the attraction object
	 * @return boolean : true if the distance is close false otherwise.
	 */
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		logger.info(" Check if the visited location is near to the attraction to be visited");
		return getDistance(attraction, visitedLocation.location) > proximityBuffer ? false : true;
	}

	/**
	 * get attraction rewards points of the giving user
	 * 
	 * @param attraction : the attraction object
	 * @param user       : the user object
	 * @return int : the attraction rewards points of the user
	 */
	public int getRewardPoints(Attraction attraction, User user) {
		logger.info(" get attraction rewards points to user : " + user.getUserName());
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
	}

	/**
	 * Calculate the distance in miles.
	 * 
	 * @param loc1 : the user's location
	 * @param loc2 : the attraction's location
	 * @return Double : the distance between the two location.
	 */
	public double getDistance(Location loc1, Location loc2) {
		logger.info(" Calculate the distance between two attractions");
		double lat1 = Math.toRadians(loc1.latitude);
		double lon1 = Math.toRadians(loc1.longitude);
		double lat2 = Math.toRadians(loc2.latitude);
		double lon2 = Math.toRadians(loc2.longitude);

		double angle = Math
				.acos(Math.sin(lat1) * Math.sin(lat2) + Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

		double nauticalMiles = 60 * Math.toDegrees(angle);
		double statuteMiles = STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
		return statuteMiles;
	}

}
