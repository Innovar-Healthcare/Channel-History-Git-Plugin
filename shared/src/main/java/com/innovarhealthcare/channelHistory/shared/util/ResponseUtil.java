package com.innovarhealthcare.channelHistory.shared.util;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for creating and parsing standardized JSON response objects for operations.
 * Uses member properties to store response data, with methods to generate success/failure responses
 * and parse JSON strings. Designed for reuse in both server and client within a shared package.
 */
public class ResponseUtil {
    private static final Logger logger = LoggerFactory.getLogger(ResponseUtil.class);

    private boolean status;
    private String message;
    private String operationDetails;

    /**
     * Default constructor for creating a new response.
     */
    public ResponseUtil() {
        this.status = false;
        this.message = "";
        this.operationDetails = "";
    }

    /**
     * Constructor to create a ResponseUtil instance from a JSON string.
     *
     * @param jsonResponse The JSON response string to parse
     * @throws IllegalArgumentException If the JSON is malformed or invalid
     */
    public ResponseUtil(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
            logger.error("JSON response is null or empty");
            this.status = false;
            this.message = "Invalid response: JSON is null or empty";
            this.operationDetails = "";
            return;
        }

        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            logger.debug("Parsing JSON response: {}", jsonResponse);

            // Check for required fields
            if (!jsonObject.has("status") || !jsonObject.has("message") || !jsonObject.has("operationDetails")) {
                logger.error("JSON response missing required fields: status, message, or operationDetails");
                this.status = false;
                this.message = "Invalid response: Missing required fields";
                this.operationDetails = "";
                return;
            }

            this.status = jsonObject.getBoolean("status");
            this.message = jsonObject.getString("message");
            this.operationDetails = jsonObject.getString("operationDetails");
            logger.debug("Parsed response: status={}, message={}, operationDetails={}", this.status, this.message, this.operationDetails);
        } catch (Exception e) {
            logger.error("Failed to parse JSON response: {}", jsonResponse, e);
            throw new IllegalArgumentException("Invalid JSON response: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a success response with the specified details.
     *
     * @param operationDetails The StringBuilder containing operation details (e.g., Git operation logs)
     * @param successMessage   The user-facing success message to include in the message
     * @return This ResponseUtil instance for method chaining
     */
    public ResponseUtil success(StringBuilder operationDetails, String successMessage) {
        if (operationDetails == null) {
            logger.warn("OperationDetails StringBuilder is null, using empty string for success response");
            operationDetails = new StringBuilder();
        }
        if (successMessage == null || successMessage.trim().isEmpty()) {
            logger.warn("SuccessMessage is null or empty, defaulting to 'Operation completed successfully'");
            successMessage = "Operation completed successfully";
        }

        logger.debug("Success operation details: {}", operationDetails.toString());
        this.status = true;
        this.message = successMessage;
        this.operationDetails = operationDetails.toString();
        return this;
    }

    /**
     * Creates a failure response with the specified details.
     *
     * @param operationDetails The StringBuilder containing operation details (e.g., Git operation logs)
     * @param errorMessage     The user-facing error message
     * @return This ResponseUtil instance for method chaining
     */
    public ResponseUtil fail(StringBuilder operationDetails, String errorMessage) {
        if (operationDetails == null) {
            logger.warn("OperationDetails StringBuilder is null, using empty string for fail response");
            operationDetails = new StringBuilder();
        }
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            logger.warn("ErrorMessage is null or empty, defaulting to 'Unknown error'");
            errorMessage = "Unknown error";
        }

        logger.debug("Fail operation details: {}", operationDetails.toString());
        this.status = false;
        this.message = errorMessage;
        this.operationDetails = operationDetails.toString();
        return this;
    }

    /**
     * Converts the ResponseUtil instance to a JSON string.
     *
     * @return JSON string representing the response
     */
    public String toJsonString() {
        try {
            JSONObject result = new JSONObject();
            result.put("status", this.status);
            result.put("message", this.message != null ? this.message : "");
            result.put("operationDetails", this.operationDetails != null ? this.operationDetails : "");
            String jsonString = result.toString();
            logger.debug("Created JSON response: {}", jsonString);
            return jsonString;
        } catch (Exception e) {
            logger.error("Failed to create JSON response: status={}, message={}, operationDetails={}",
                    this.status, this.message, this.operationDetails, e);
            return "{\"status\":false,\"message\":\"Internal error creating response: " + e.getMessage() + "\",\"operationDetails\":\"\"}";
        }
    }

    /**
     * Returns true if the operation was successful, false otherwise.
     */
    public boolean isSuccess() {
        return this.status;
    }

    /**
     * Gets the user-facing message for the operation.
     */
    public String getMessage() {
        return this.message != null ? this.message : "";
    }

    /**
     * Gets the operation details for system logging and future use.
     */
    public String getOperationDetails() {
        return this.operationDetails != null ? this.operationDetails : "";
    }
}