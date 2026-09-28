package com.jonathansoriano.enterprisedevgroupproject.exception;

import lombok.Data;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class ExceptionWrapper
{
    private String timeStamp;
    private Integer status;
    private String error;
    private String message;
    private String path;

    /**
     * Field name to message, for a validation failure; null for every other error.
     *
     * <p>Added because the alternative was what this class used to do: jam a
     * {@code Map.toString()} into {@link #message}, so a student read
     * {@code {bio=Bio must be 1000 characters or fewer}} and a client could not tell which
     * field to mark without parsing that. The message is still a readable sentence for
     * anything that only logs it; this is for anything that wants to act on it.
     *
     * <p>Serialised as null rather than omitted, which keeps the shape of every other
     * error response unchanged — clients already treat a falsy value as "no field errors".
     */
    private Map<String, String> fieldErrors;

    //PURPOSE OF THIS CLASS: This class:
    //1. Makes your error responses consistent
    //2. Gives clients useful info like timestamp, status, and path of URL
    //3. Makes debugging and client integration easier
    public ExceptionWrapper(Integer status, String message, String path)
    {
        this.timeStamp = LocalDateTime.now().toString();
        this.status = status;
        this.error = HttpStatus.valueOf(status).getReasonPhrase();
        this.message = message;
        this.path = path;
    }

    /** Validation failures, which additionally carry the per-field detail. */
    public ExceptionWrapper(Integer status, String message, String path, Map<String, String> fieldErrors) {
        this(status, message, path);
        this.fieldErrors = fieldErrors;
    }


}
