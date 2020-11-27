package org.acme.registration;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.jboss.logging.Logger;

/**
 * @author laurent
 */
@Path("/")
public class UserRegistrationResource {

    /** Get a JBoss logging logger. */
    private final Logger logger = Logger.getLogger(getClass());

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response processRegistration(String cloudEventJson) {
        logger.info("cloudEventJson: " + cloudEventJson);

        ObjectMapper mapper = new ObjectMapper();

        try {
            // Parse cloud event in JSON.
            UserRegistration registration = mapper.readValue(cloudEventJson, UserRegistration.class);

            // Process registration logic here...
            logger.infof("Processing registration {%s} for {%s}", registration.getId(), registration.getFullName());
        } catch (Exception e) {
            logger.error("Exception while processing registration CloudEvent", e);
            return Response.notModified().build();
        }
        return Response.ok().build();
    }
}