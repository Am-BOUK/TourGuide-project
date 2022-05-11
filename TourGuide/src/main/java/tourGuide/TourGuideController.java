package tourGuide;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jsoniter.output.JsonStream;

import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import tourGuide.service.TourGuideService;
import tourGuide.user.User;
import tripPricer.Provider;

/**
 * Implementing the management of interactions between the application TourGuide
 * and the application.
 *
 */
@RestController
public class TourGuideController {
	private Logger logger = LoggerFactory.getLogger(TourGuideController.class);

	/**
	 * Inject TourGuide service
	 */
	@Autowired
	TourGuideService tourGuideService;

	/**
	 * Get home page endpoint
	 * 
	 * @return String : Greetings from TourGuide!
	 */
	@RequestMapping("/")
	public String index() {
		logger.info("Get home page endpoint");
		return "Greetings from TourGuide!";
	}

	/**
	 * Get user's location endpoint as JSON
	 * 
	 * @param userName : the userName of the user we want to get his location
	 * @return String : a JSON mapping of the location of the user
	 */
	@RequestMapping("/getLocation")
	public String getLocation(@RequestParam String userName) {
		logger.info("Get user's location */getLocation* of the user : " + userName);
		VisitedLocation visitedLocation = tourGuideService.getUserLocation(getUser(userName));
		return JsonStream.serialize(visitedLocation.location);
	}

	/**
	 * Get the closest five tourist attractions to the user as JSON
	 * 
	 * @param userName
	 * @return String : a JSON mapping of the closest five tourist attractions to
	 *         the userName
	 */
	@RequestMapping("/getNearbyAttractions")
	public String getNearbyAttractions(@RequestParam String userName) {
		logger.info("Get the closest five tourist attractions */getNearbyAttractions* of the user : " + userName);
		VisitedLocation visitedLocation = tourGuideService.getUserLocation(getUser(userName));
		return JsonStream.serialize(tourGuideService.getNearByAttractions(visitedLocation));
	}

	/**
	 * Get rewards of the user endpoint
	 * 
	 * @param userName : the userName of the user we want to get his rewards
	 * @return String : a JSON mapping of rewards of the user
	 */
	@RequestMapping("/getRewards") // vide!!!
	public String getRewards(@RequestParam String userName) {
		logger.info("Get rewards */getRewards* of the user : " + userName);
		return JsonStream.serialize(tourGuideService.getUserRewards(getUser(userName)));
	}

	/**
	 * Get a list of every user's most recent location endpoint as JSON
	 * 
	 * @return String : a JSON mapping of userId to Locations
	 */
	@RequestMapping("/getAllCurrentLocations")
	public String getAllCurrentLocations() {
		logger.info("Get a list of every user's most recent location */getAllCurrentLocations*");
		Map<String, Location> getAllCurrentLocations = tourGuideService.getAllCurrentLocations();
		return JsonStream.serialize(getAllCurrentLocations);
//		return getAllCurrentLocations;
	}

	/**
	 * Get trip deals of the user endpoint as JSON
	 * 
	 * @param userName : the userName of the user we want to get his trip deals
	 * @return String : a JSON mapping of trip deals of the user
	 */
	@RequestMapping("/getTripDeals")
	public String getTripDeals(@RequestParam String userName) {
		logger.info("Get trip deals */getTripDeals* of the user : " + userName);
		List<Provider> providers = tourGuideService.getTripDeals(getUser(userName));
		return JsonStream.serialize(providers);
	}

	private User getUser(String userName) {
		return tourGuideService.getUser(userName);
	}

}