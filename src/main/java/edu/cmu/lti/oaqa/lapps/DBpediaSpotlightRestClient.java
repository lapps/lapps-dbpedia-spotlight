package edu.cmu.lti.oaqa.lapps;

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * DBpedia Spotlight Rest client.
 *
 * @author Di Wang
 */

public class DBpediaSpotlightRestClient {


    private static final String SPOTLIGHT_ENDPOINT = "http://spotlight.dbpedia.org/";
    private static final int NUM_RETRY = 3;

    private static final double CONFIDENCE = 0.0;
    private static final int SUPPORT = 0;

    // Create an instance of HttpClient.
    protected static HttpClient client = new HttpClient();

    public String request(HttpMethod method) {
        String response = null;
        method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER,
                new DefaultHttpMethodRetryHandler(NUM_RETRY, false));
        try {
            int statusCode = client.executeMethod(method);
            if (statusCode != HttpStatus.SC_OK) {
                System.err.println("Method failed: " + method.getStatusLine());
            }
            byte[] responseBody = method.getResponseBody();
            response = new String(responseBody);
        } catch (HttpException e) {
            System.err.println("Fatal protocol violation: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Fatal transport error: " + e.getMessage());
            System.err.println(method.getQueryString());
        } finally {
            method.releaseConnection();
        }
        return response;
    }

    public JSONObject fetchJson(String text) {

        String spotlightResponse = null;
        GetMethod getMethod = new GetMethod(makeQueryUrl(text));
        getMethod.addRequestHeader(new Header("Accept", "application/json"));
        spotlightResponse = request(getMethod);

        assert spotlightResponse != null;

        JSONObject resultJSON = null;

        try {
            resultJSON = new JSONObject(spotlightResponse);
        } catch (JSONException e) {
            System.err.println("Received invalid response from DBpedia Spotlight API.");
        }
        return resultJSON;
    }

    protected String makeQueryUrl(String query) {
        String url = null;
        try {
            url = SPOTLIGHT_ENDPOINT + "rest/annotate/?" + "confidence=" + CONFIDENCE + "&support=" + SUPPORT
                    + "&text=" + URLEncoder.encode(query, "utf-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return url;
    }

}
