package tourGuide.controllers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.jsoniter.output.JsonStream;

import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import tourGuide.exceptions.UserNameNotFoundException;
import tourGuide.exceptions.UserPreferencesException;
import tourGuide.service.TourGuideService;
import tourGuide.user.User;
import tourGuide.user.UserPreferences;
import tripPricer.Provider;

/**
 * Implementing the management of interactions between the application TourGuide
 * and the application.
 *
 */
@RestController
public class TourGuideController {
	private Logger logger = LoggerFactory.getLogger(TourGuideController.class);

	@Autowired
	TourGuideService tourGuideService;

	/**
	 * Get user's location endpoint as JSON
	 * 
	 * @param userName : the userName of the user we want to get his location
	 * @return String : a JSON mapping of the location of the user
	 * @throws UserNameNotFoundException
	 */
	@RequestMapping("/getLocation")
	public String getLocation(@RequestParam String userName) throws UserNameNotFoundException {
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
	 * @throws UserNameNotFoundException
	 */
	@RequestMapping("/getNearbyAttractions")
	public String getNearbyAttractions(@RequestParam String userName) throws UserNameNotFoundException {
		logger.info("Get the closest five tourist attractions */getNearbyAttractions* of the user : " + userName);
		VisitedLocation visitedLocation = tourGuideService.getUserLocation(getUser(userName));
		return JsonStream.serialize(tourGuideService.getNearByAttractions(visitedLocation));

	}

	/**
	 * Get rewards of the user endpoint
	 * 
	 * @param userName : the userName of the user we want to get his rewards
	 * @return String : a JSON mapping of rewards of the user
	 * @throws UserNameNotFoundException
	 */
	@RequestMapping("/getRewards")
	public String getRewards(@RequestParam String userName) throws UserNameNotFoundException {
		logger.info("Get rewards */getRewards* of the user : " + userName);
		return JsonStream.serialize(tourGuideService.getUserRewards(getUser(userName)));
	}

	/**
	 * Get a list of every user's most recent location endpoint as JSON
	 * 
	 * @return String : a JSON mapping of userId to Locations
	 * @throws UserNameNotFoundException
	 */
	@RequestMapping("/getAllCurrentLocations")
	public String getAllCurrentLocations() {
		logger.info("Get a list of every user's most recent location */getAllCurrentLocations*");
		Map<String, Location> getAllCurrentLocations = tourGuideService.getAllCurrentLocations();
		return JsonStream.serialize(getAllCurrentLocations);
	}

	/**
	 * Get trip deals of the user endpoint as JSON
	 * 
	 * @param userName : the userName of the user we want to get his trip deals
	 * @return String : a JSON mapping of trip deals of the user
	 * @throws UserNameNotFoundException
	 */
	@RequestMapping("/getTripDeals")
	public String getTripDeals(@RequestParam String userName) throws UserNameNotFoundException {
		logger.info("Get trip deals */getTripDeals* of the user : " + userName);
		List<Provider> providers = tourGuideService.getTripDeals(getUser(userName));
		return JsonStream.serialize(providers);
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
	@PutMapping("/putUserPreferences")
	public UserPreferences updatePreferences(@RequestParam String userName,
			@RequestBody UserPreferences userPreferences) throws UserNameNotFoundException, UserPreferencesException {
		logger.info("Access to /userPreferences endpoint with username : " + userName);
		logger.debug("Access to /userPreferences endpoint with UserPreferencesDTO as a body : " + userPreferences);
		UserPreferences updatePreferences = tourGuideService.updateUserPreferences(userName, userPreferences);
//		String jsonStringFromObject = JSON.toJSONString(updatePreferences);
		return updatePreferences;
	}

	/**
	 * Handle specified types of exceptions ** Processing the conflict errors:
	 * 
	 * @param ex argument of method not valid
	 * @return message of errors
	 */
	@ResponseStatus(HttpStatus.CONFLICT)
	@ExceptionHandler(Exception.class)
	public Map<String, String> handleExceptions(Exception ex) {
		Map<String, String> errors = new HashMap<>();
		String fieldName = "Error";
		String errorMessage = ex.getMessage();
		errors.put(fieldName, errorMessage);

		logger.info("the specified user object is invalid : " + errors);
		return errors;
	}

	/**
	 * Handle specified types of exceptions ** Processing the validation errors:
	 * 
	 * @param ex argument of method not valid
	 * @return
	 */
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public Map<String, String> handleValidationExceptions(MethodArgumentNotValidException ex) {
		Map<String, String> errors = new HashMap<>();
		ex.getBindingResult().getAllErrors().forEach((error) -> {
			String fieldName = ((FieldError) error).getField();
			String errorMessage = error.getDefaultMessage();
			errors.put(fieldName, errorMessage);
		});
		logger.info("the specified tourguide object is invalid : " + errors);
		return errors;
	}

	private User getUser(String userName) throws UserNameNotFoundException {
		return tourGuideService.getUser(userName);
	}

}