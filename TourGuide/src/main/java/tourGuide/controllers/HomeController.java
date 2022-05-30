package tourGuide.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implementing the management of interactions between the application TourGuide
 * and the application.
 *
 */
@RestController
public class HomeController {
	private Logger logger = LoggerFactory.getLogger(TourGuideController.class);

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

}
