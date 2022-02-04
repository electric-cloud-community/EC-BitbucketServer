import groovy.json.JsonSlurper
import com.electriccloud.errors.EcException
import java.util.regex.Pattern
import com.electriccloud.errors.ErrorCodes

def trigger = args.trigger
Map<String, String> headers = args.headers
String method = args.method
String body = args.body
//String url = args.url
//def query = args.query

// This map corresponds to the procedure form
final Map<String, Map<String, String>> SUPPORTED_EVENTS = [
    'push'        : [
        enabledParamName: 'pushEvent',
        actionsParamName: null
    ],
    'pr': [
        enabledParamName: 'prEvent',
        actionsParamName: 'includePrActions'
    ]
]

// Checking API compliance
if (method != 'POST') {
    throw EcException.code(ErrorCodes.WebhookProcessingError)
        .message("Unsupported method")
        .build()
}

// Parsing headers
String eventHeader = headers.get('x-event-key')

if (!eventHeader) {
    throw EcException.code(ErrorCodes.WebhookProcessingError)
        .message("Request does not contain the event header")
        .build()
}

// As we do not use other restrictions, every trigger should have a signature secret
//if (!trigger.webhookSecret) {
//    throw new RuntimeException("Trigger '${trigger.getName()}' does not have webhookSecret set up")
//}
//validating signature
//if (!verifySignedPayload(signature, (String) trigger.webhookSecret, body)) {
//     Todo: change to agreed exception
//We don't have secret on the bitbucket webhooks, at least in the bitbucket cloud
//    throw new RuntimeException("Signatures do not match. Please check that the trigger's 'webhookSecret' field value matches one specified in the Github repository webhook settings.")
//}

// Receiving trigger parameters
def paramsPropertySheet = trigger.pluginParameters
Map<String, String> pluginParameters = [:]
paramsPropertySheet['properties'].each { String k, Map<String, String> v ->
    pluginParameters[k] = v['value']
}

WebhookEvent webhookEvent = WebhookEvent.getForType(eventHeader, body)
if (webhookEvent == null) {
    return [
        responseMessage: "Ignoring unsupported '${eventHeader}' event".toString(),
        eventType      : eventHeader,
        launchWebhook  : false
    ]
}

String event = webhookEvent.getName()

// Check repository
String repositoriesFilter = pluginParameters.get('repositories')
if (repositoriesFilter) {
    String repositoryName = webhookEvent.getRepositoryName()
    if (!repositoryName) {
        throw EcException.code(ErrorCodes.WebhookProcessingError)
            .message("Webhook event '${event}' doesn't contain 'repository' object")
            .build()
    }
    if (!doCheckRepositoryIncluded(repositoriesFilter, repositoryName)) {
        return [
            responseMessage: "Ignoring '${repositoryName}' repository event".toString(),
            repositoryName : repositoryName,
            launchWebhook  : false
        ]
    }
}

//if (event == 'ping') {
//    return [
//        responseMessage: 'zen',
//        launchWebhook  : false
//    ]
//}

// Check event enabled
String eventEnabledParamName = SUPPORTED_EVENTS[event]['enabledParamName']
if (eventEnabledParamName != null) {
    boolean eventEnabled = pluginParameters.get(eventEnabledParamName) == 'true'
    if (!eventEnabled) {
        return [
            responseMessage: "Processing for the '${webhookEvent.getName()}' event is disabled".toString(),
            eventType      : event,
            launchWebhook  : false
        ]
    }
}


// Check action enabled
String includedActionParamName = SUPPORTED_EVENTS[event]['actionsParamName']
if (includedActionParamName != null) {
    String includedActions = pluginParameters.get(includedActionParamName)
    if (includedActions) {
        String eventAction = webhookEvent.getAction()
        boolean actionEnabled = doCheckActionIncluded(includedActions, eventAction)
        if (!actionEnabled) {
            return [
                responseMessage: "Processing for the '${eventAction}' action of the '${event}' event is disabled".toString(),
                eventType      : event,
                action         : eventAction,
                launchWebhook  : false
            ]
        }
    }
}

// Check that branch is included and not excluded
String includeBranches = pluginParameters.get('includeBranches')
String excludeBranches = pluginParameters.get('excludeBranches')

ArrayList<String> eventBranches = webhookEvent.getBranchNames()
String branchName = eventBranches.join(', ')
if (includeBranches) {
    if (!doCheckBranchIncluded(includeBranches, eventBranches)) {
        return [
            responseMessage: "Ignoring '${event}' event for branch '${branchName}'".toString(),
            eventType      : event,
            branch         : branchName,
            launchWebhook  : false
        ]
    }
}
if (excludeBranches) {
    if (doCheckBranchIncluded(excludeBranches, eventBranches)) {
        return [
            responseMessage: "Ignoring '${event}' event for excluded branch '${branchName}'".toString(),
            eventType      : event,
            branch         : branchName,
            launchWebhook  : false
        ]
    }
}

String responseMessage = "Launching trigger '${trigger['name']}' for event '${event}'"
if (webhookEvent.getAction()) {
    responseMessage += " with state/action '${webhookEvent.getAction()}'"
}
if (webhookEvent.getBranchNames().size()) {
    responseMessage += " in branch(es) '${webhookEvent.getBranchNames().join(', ')}'"
}
if (webhookEvent.getRepositoryName()) {
    responseMessage += " of the '${webhookEvent.getRepositoryName()}' repository."
}

// Collect data for response
Map<String, String> webhookData = webhookEvent.collectWebhookData()
Map<String, String> recentCommit = webhookEvent.getRecentCommit()

Map<String, Object> response = [
    eventType      : event,
    launchWebhook  : true,
    branch         : webhookEvent.getBranchNames().join(', '),
    repository     : webhookEvent.getRepositoryName(),
    responseMessage: responseMessage.toString()
] as Map<String, Object>

if (webhookData) {
    response['webhookData'] = webhookData
}

if (recentCommit) {
    response['commitId'] = recentCommit['commitId']
    response['commitMessage'] = recentCommit['commitMessage']
    response['commitAuthorName'] = recentCommit['commitAuthorName']
    response['commitAuthorEmail'] = recentCommit['commitAuthorEmail']
}

return response

/**
 * These methods depend on the form declaration
 */

boolean doCheckRepositoryIncluded(String parameterValue, String checked) {
    ArrayList<String> list = parameterValue.tokenize("\n").collect({ it.trim() })
    return list.contains(checked)
}

boolean doCheckActionIncluded(String parameterValue, String checked) {
    ArrayList<String> list = parameterValue.tokenize(/, +?/).collect({ it.trim() })
    return list.contains(checked)
}

boolean doCheckBranchIncluded(String parameterValue, ArrayList<String> checked) {
    ArrayList<String> list = parameterValue.tokenize(/, +?/).collect({ it.trim() })
    for (String b : checked) {
        if (listContainsGlobMatch(list, b)) {
            return true
        }
    }
    return false
}

//////////////////////////////////////////////////////////////////////////////////////////////
// End of business logic
//////////////////////////////////////////////////////////////////////////////////////////////


boolean listContainsGlobMatch(ArrayList<String> list, String checked) {
    for (String l : list) {
        def pattern = Pattern.compile(l)
        if (pattern.matcher(checked).lookingAt()) {
            return true
        }
    }
    return false
}

abstract class WebhookEvent {
    abstract String name
    abstract String body
    abstract String action

    Map<String, Object> payload

    WebhookEvent(String payload) {
        this.body = payload
        this.payload = (new JsonSlurper()).parseText(this.body) as Map<String, Object>
    }

    static WebhookEvent getForType(String event, String payload) {
        if (event =~ /^pr:/) {
            return new PullRequestEvent(payload, event)
        } else if (event == 'repo:refs_changed') {
            return new PushEvent(payload)
        } else {
            return null
        }
    }

    abstract String getRepositoryName()

    abstract ArrayList<String> getBranchNames()

    abstract Map<String, String> getRecentCommit()

    abstract Map<String, String> collectWebhookData()

}

class PullRequestEvent extends WebhookEvent {

    static String name = 'pr'
    String action = null

    String getRepositoryName(){
        String repositoryName =  payload.get('pullRequest')?.get('fromRef')?.get('repository')?.get('name') as String
        String projectName = payload.get('pullRequest')?.get('fromRef')?.get('repository')?.get('project')?.get("key") as String
        return "${projectName}/${repositoryName}"
    }

    ArrayList<String> getBranchNames() {
        String branchName = payload.get('pullRequest')?.get('fromRef')?.get('displayId') as String
        if (!branchName) {
            return null
        }
        return [branchName]
    }

    PullRequestEvent(String payload, String event) {
        super(payload)
        this.action = event.replaceAll(/^pr:/, '')
    }

    @Override
    Map<String, String> getRecentCommit() {
        def commitId = payload.get('pullRequest')?.get('fromRef')?.get('latestCommit')
        if (commitId == null) return null

        // Pull request author may not be the same as the commit author,
        // but we don't have any other commit information
        def authorName = payload.get('pullRequest')?.get('author')?.get('user')?.get('displayName')
        def mail = payload.get('pullRequest')?.get('author')?.get('user')?.get('emailAddress')

        return [
            commitId         : commitId,
//            commitMessage    : target['message'].toString(),
            commitAuthorName : authorName,
            commitAuthorEmail: mail,
        ]
    }

    @Override
    Map<String, String> collectWebhookData() {
        def pullRequest = payload['pullRequest']

        return [
            number: pullRequest['id'],
            title : pullRequest['title'],
            body  : pullRequest['description'],
            state : pullRequest['state'],
            author: pullRequest['author']['user']['displayName'],
            url   : pullRequest['links'].get('self')?.first()?.get('href')
        ] as Map<String, String>
    }

}

class PushEvent extends WebhookEvent {

    static String name = 'push'
    String action = null

    PushEvent(String payload) {
        super(payload)
    }

    String getRepositoryName(){
        String repositoryName =  payload.get('repository')?.get('name') as String
        String projectName = payload.get('repository')?.get('project')?.get("key") as String
        return "${projectName}/${repositoryName}"
    }

    def getChangesNew() {
        def changes = (ArrayList<Map<String, Object>>) payload['changes']

        if (!changes || !changes.size()) {
            throw EcException.code(ErrorCodes.WebhookProcessingError)
                .message("Not a commit push")
                .build()
        }

        Map<String, Object> afterPush = changes.first()
        if (afterPush == null) {
            // Branch delete was pushed
            return null
        }

        if (afterPush.get('ref')?.get('type') != 'BRANCH') {
            // This is not a branch push
            throw EcException.code(ErrorCodes.WebhookProcessingError)
                .message("Only the branch 'push' events are supported, got '${afterPush.get('ref')?.get('type')}'")
                .build()
        }

        return afterPush
    }

    ArrayList<String> getBranchNames() {
        Map<String, Object> afterPush = getChangesNew()

        String branchName = afterPush.get('ref')?.get('displayId') as String
        if (!branchName) {
            return null
        }

        return [branchName]
    }

    @Override
    Map<String, String> getRecentCommit() {
        Map<String, Object> target = getChangesNew() as Map<String, Object>

        String username = payload.get('actor')?.get('displayName') as String
        String mail = payload.get('actor')?.get('emailAddress') as String

        return [
            commitId         : target['toHash'].toString(),
            commitAuthorName : username,
            commitAuthorEmail: mail,
        ]
    }

    @Override
    Map<String, String> collectWebhookData() {
        return [
            branch: getBranchNames().join(',')
        ]
    }
}
