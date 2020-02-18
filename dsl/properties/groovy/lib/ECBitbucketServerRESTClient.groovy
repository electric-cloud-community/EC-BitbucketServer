// DO NOT EDIT THIS BLOCK === rest client imports starts ===
import com.cloudbees.flowpdf.*
import com.cloudbees.flowpdf.client.HTTPRequest
import com.cloudbees.flowpdf.client.REST
import com.cloudbees.flowpdf.client.RESTConfig
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.InheritConstructors
import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import org.apache.http.HttpException
import org.apache.http.HttpResponse
import sun.reflect.generics.reflectiveObjects.NotImplementedException
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
// DO NOT EDIT THIS BLOCK === rest client imports ends, checksum: bd43b259ee4d532c8bd07cf2f708baba ===
// Place for the custom user imports, e.g. import groovy.xml.*
// DO NOT EDIT THIS BLOCK === rest client starts ===
@InheritConstructors
class InvalidRestClientException extends Exception {

}

class ProxyConfig {
    String url
    String userName
    String password
}

class ECBitbucketServerRESTClient {

    private static String BEARER_PREFIX = 'Bearer'
    private static String USER_AGENT = 'ECBitbucketServerRESTClient REST Client'
    private static String CONTENT_TYPE = 'application/json'
    private static OAUTH1_SIGNATURE_METHOD = 'RSA-SHA1'

    String endpoint
    String method
    Map<String, String> methodParameters

    private Log log
    private REST rest
    private ProxyConfig proxyConfig

    ECBitbucketServerRESTClient(String endpoint, RESTConfig restConfig, FlowPlugin plugin) {
        this.endpoint = endpoint
        this.log = plugin.log
        this.rest = new REST(restConfig)
    }

    /**
     * Will create a ECBitbucketServerRESTClient object from the plugin Config object.
     * Convenient as it can use pre-defined configuration fields.
     */
    static ECBitbucketServerRESTClient fromConfig(Config config, FlowPlugin plugin) {
        Map params = [:]
        String endpoint = config.getRequiredParameter('endpoint').value.toString()
        Log log = plugin.log
        Credential credential
        RESTConfig restConfig = new RESTConfig()
        restConfig.endpoint = endpoint
        if ((credential = config.getCredential('bearer_credential')) && credential.secretValue) {
            restConfig.authScheme = 'bearer'
            restConfig.bearerToken = credential.secretValue
            restConfig.bearerPrefix = BEARER_PREFIX
            log.debug "Using bearer credential in REST Client"
        }
        else if ((credential = config.getCredential('basic_credential'))) {
            restConfig.authScheme = 'basic'
            restConfig.basicCredentialUsername = credential.userName
            restConfig.basicCredentialPassword = credential.secretValue
        }
        else if (config.isParameterHasValue('authScheme') && config.getParameter('authScheme').value == 'anonymous') {
            log.debug "Using anonymous auth scheme"
        }
        if (config.isParameterHasValue('httpProxyUrl')) {
            String proxyUrl = config.getParameter('httpProxyUrl').value
            restConfig.httpProxyUrl = proxyUrl
            log.debug "Using proxy $proxyUrl"
            if ((credential = config.getCredential('proxy_credential'))) {
                restConfig.proxyCredentialUsername = credential.userName
                restConfig.proxyCredentialPassword = credential.secretValue
                log.debug "Using proxy authorization"
            }
        }
        return new ECBitbucketServerRESTClient(endpoint, restConfig, plugin)
    }

    // Handles templates like , taking values from the params
    private static String renderOneLineTemplate(String uri, Map params) {
        for(String key in params.keySet()) {
            Object value = params.get(key)
            if (value) {
                uri = uri.replaceAll(/\{\{$key\}\}/, value as String)
            }
            else {
                throw new InvalidRestClientException("A field $key is empty in params but required in the template")
            }
        }
        return uri
    }

    /**
     * This is the main request method
     * methodString - GET|POST|PUT - request method
     * url - uri path (without the endpoint)
     * query - uri.query
     * payload - payload for POST/PUT requests
     * h - headers for the request
     */
    Object makeRequest(String methodString, String url, Map query, def payload, Map h) {
        Method method = Method.valueOf(methodString)

        HTTPRequest request = new HTTPRequest(
            method: method,
            path: url,
            query: query,
            headers: h
        )

        if (payload) {
            if (payload instanceof byte[]) {
                request.setContentBytes(payload)
            }
            else {
                request.setContentString(encodePayload(payload))
            }
        }

        if (method == Method.POST || method == Method.PUT) {
            if (!request.requestContentType) {
                request.requestContentType = ContentType.JSON
            }
        }
        request = rest.prepareRequest(request)
        request = augmentRequest(request)

        HTTPBuilder builder = rest.httpBuilder
        boolean success = true
        Tuple2 responseTuple = builder.request(method) {
            if (request.path) {
                uri.path = request.path
                log.trace("Set request path: $uri.path")
            }
            if (request.query) {
                uri.query = request.query
            }
            request.headers.each { headerName, headerValue ->
                headers.put(headerName, headerValue)
                log.trace "Added request hheader $headerName -> $headerValue"
            }
            if (request.contentString) {
                send request.requestContentType, request.contentString
                log.trace "Request body is $body"
            }

            if (request.contentBytes) {
                send request.requestContentType, request.contentBytes
            }

            response.success = { HttpResponse resp, decoded ->
                log.trace "Got response: $resp"
                log.trace "Got content: $decoded"

                return new Tuple2(resp, decoded)
            }
            response.failure = { HttpResponse resp, body ->
                log.trace "Request failed: $resp"
                log.trace "$body"
                success = false

                return new Tuple2(resp, body)
            }
        }
        HttpResponse response = responseTuple.first
        Object body = responseTuple.second

        Object processedResponse = processResponse(response, body)
        if (processedResponse) {
            return processedResponse
        }
        if (!success) {
            throw new HttpException("Request for uri $url failed: ${response.statusLine.statusCode}, ${body}")
        }
        Object parsed = parseResponse(response, body)
        return parsed
    }

    private static payloadFromTemplate(String template, Map params) {
        Object object = new JsonSlurper().parseText(template)
        object = fillFields(object, params)
        return object
    }

    private static def fillFields(def o, Map params) {
        def retval
        if (o instanceof Map) {
            retval = [:]
            for(String key in o.keySet()) {
                key = renderOneLineTemplate(key, params)
                def value = o.get(key)
                if (value instanceof String) {
                    value = renderOneLineTemplate(value, params)
                }
                else {
                    value = fillFields(value, params)
                }
                retval.put(key, value)
            }
        }
        else if (o instanceof List) {
            retval = []
            for (def i in o) {
                i = fillFields(i, params)
                retval.add(i)
            }
        }
        else if (o instanceof String) {
            o = renderOneLineTemplate(o, params)
            retval = o
        }
        else {
            throw new NotImplementedException()
        }
        return retval
    }

    /** Generated code for the endpoint /rest/api/1.0/projects/{{projectKey}}/repos/{{repositorySlug}}/pull-requests/{{pullRequestId}}/merge
    * Do not change this code
    * projectKey: in path
    * repositorySlug: in path
    * pullRequestId: in path
    * version: in query
    */
    def mergePullRequest(Map<String, String> params) {
        this.method = 'mergePullRequest'
        this.methodParameters = params

        String uri = '/rest/api/1.0/projects/{{projectKey}}/repos/{{repositorySlug}}/pull-requests/{{pullRequestId}}/merge'
        log.debug("URI template $uri")
        uri = renderOneLineTemplate(uri, params)

        Map query = [:]

        query.put('version', params.get('version'))

        log.debug "Query: ${query}"

        Object payload

        String jsonTemplate = ''
        if (jsonTemplate) {
            payload = payloadFromTemplate(jsonTemplate, params)
            log.debug("Payload from template: $payload")
        }
        //TODO clean empty fields
        Map headers = [:]
        return makeRequest('POST', uri, query, payload, headers)
    }
// DO NOT EDIT THIS BLOCK === rest client ends, checksum: d2f69329eb4be497af0a54c13f4b4273 ===
    /**
     * Use this method for any request pre-processing: adding custom headers, binary files, etc.
     */
    HTTPRequest augmentRequest(HTTPRequest request) {
        return request
    }

    /**
     * Use this method to provide a custom encoding for you payload (XML, yaml etc)
     */
    Object encodePayload(def payload) {
        return JsonOutput.toJson(payload)
    }

    /**
     * Use this method to parse/alter response from the server
     */
    def parseResponse(HttpResponse response, Object body) {
        //Relying on http builder content type processing
        return body
    }

    /**
     * Use this method to alter default server response processing.
     * The response from this method will be returned as is, if any.
     * To disable response, just return null.
     */
    def processResponse(HttpResponse response, Object body) {
        return null
    }

}