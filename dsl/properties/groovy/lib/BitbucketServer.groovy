import com.cloudbees.flowpdf.*
import groovy.json.JsonOutput

/**
* BitbucketServer
*/
class BitbucketServer extends FlowPlugin {

    @Override
    Map<String, Object> pluginInfo() {
        return [
                pluginName     : '@PLUGIN_KEY@',
                pluginVersion  : '@PLUGIN_VERSION@',
                configFields   : ['config'],
                configLocations: ['ec_plugin_cfgs'],
                defaultConfigValues: [:]
        ]
    }

/**
     * Auto-generated method for the procedure Merge Pull Request/Merge Pull Request
     * Add your code into this method and it will be called when step runs* Parameter: config* Parameter: projectKey* Parameter: repositorySlug* Parameter: pullRequestId* Parameter: version
     */
    def mergePullRequest(StepParameters p, StepResult sr) {
        ECBitbucketServerRESTClient rest = genECBitbucketServerRESTClient()
        Map restParams = [:]
        Map requestParams = p.asMap
        restParams.put('projectKey', requestParams.get('projectKey'))
        restParams.put('pullRequestId', requestParams.get('pullRequestId'))
        restParams.put('version', requestParams.get('version'))

        Object response = rest.mergePullRequest(restParams)
        log.info "Got response from server: $response"
        log.debug("Response: " + response.response())
        
        // Saving issue as a JSON
        def jsonResultStr = JsonOutput.toJson(response)
        log.debug("Merge Response JSON: " + jsonResultStr)
    
        sr.setOutputParameter('BitBucketMergeResponse', jsonResultStr)
        sr.apply()
    }
/**
     * This method returns REST Client object
     */
    ECBitbucketServerRESTClient genECBitbucketServerRESTClient() {
        Context context = getContext()
        ECBitbucketServerRESTClient rest = ECBitbucketServerRESTClient.fromConfig(context.getConfigValues(), this)
        return rest
    }
// === step ends ===

}