
// DO NOT EDIT THIS BLOCK BELOW=== check_connection starts ===
import groovy.json.JsonSlurper
import com.electriccloud.client.groovy.ElectricFlow
import groovyx.net.http.HTTPBuilder
import static groovyx.net.http.Method.GET
import static groovyx.net.http.ContentType.TEXT
import static groovyx.net.http.ContentType.JSON
import org.apache.http.auth.*

def checkConnectionMetaString = '''
{"authSchemes":{"bearerToken":{"checkConnectionUri":null,"prefix":null,"credentialName":"bearer_credential"},"basic":{"checkConnectionUri":null,"credentialName":"basic_credential"},"anonymous":{"checkConnectionUri":"/rest/api/1.0/projects"}},"checkConnectionUri":"/rest/api/1.0/projects","headers":{"Accept":"application/json"}}
'''

def checkConnectionMeta = new JsonSlurper().parseText(checkConnectionMetaString)
println "Check Connection Metadata: $checkConnectionMeta"

ElectricFlow ef = new ElectricFlow()
def formalParameters = ef.getFormalParameters(
    projectName: '$[/myProject/name]',
    procedureName: '$[/myProcedure/name]'
)?.formalParameter

println "Formal parameters: $formalParameters"

def endpoint = ef.getProperty(propertyName: "endpoint")?.property?.value
println "Endpoint: $endpoint"
if (!endpoint) {
    handleError("Endpoint is not found (endpoint field does not exist?)")
}
def authType
try {
    authType = ef.getProperty(propertyName: "authScheme")?.property?.value
} catch (Throwable e) {
    // Deduce auth type
    // If we don't have a parameter for auth type, then we have only one auth type and it should be declared in meta
    authType = checkConnectionMeta?.authSchemes?.keySet().first()
    if (!authType) {
        handleError("Cannot deduce auth type: unclear metadata $checkConnectionMetaString")
    }
    println "Deduced Auth Scheme: $authType"
}
println "Auth Scheme: $authType"

def http = new HTTPBuilder(endpoint)

def proxyUrlFormalParameter = formalParameters.find { it.formalParameterName == 'httpProxyUrl'}
if (proxyUrlFormalParameter) {
  def proxyUrl
  try {
    proxyUrl = ef.getProperty(propertyName: "/myCall/httpProxyUrl")?.property?.value
  } catch  (Throwable e) {
  }
  // Need to split into scheme, host and port
  if (proxyUrl) {
    URL url = new URL(proxyUrl)
    http.setProxy(url.host, url.port, url.protocol)
    println "Set proxy $proxyUrl"

    def proxyCredential
    try {
      proxyCredential = ef.getFullCredential(credentialName: 'proxy_credential')?.credential
    } catch(Throwable e) {
    }

    if (proxyCredential && proxyCredential.userName) {
      http.setProxy(url.host, url.port, 'http')
      http.client.getCredentialsProvider().setCredentials(
        new AuthScope(url.host, url.port),
        new UsernamePasswordCredentials(proxyCredential.userName, proxyCredential.password)
      )
      println "Set proxy auth"
    }
  }
}

// Should be ignored after the proxy is set
http.ignoreSSLIssues()

http.request(GET, TEXT) { req ->
  headers.'User-Agent' = 'FlowPDF Check Connection'
  headers.accept = '*'

  if (checkConnectionMeta.headers) {
    def h = checkConnectionMeta.headers
    h.each {k, v ->
      headers.put(k.toLowerCase(), v)
    }
    println "Added headers: $checkConnectionMeta.headers"
  }
  uri.query = [:]

  boolean uriChanged = false
  if (authType == "basic") {
    def meta = checkConnectionMeta?.authSchemes?.basic
    def credentialName = meta?.credentialName ?: "basic_credential"
    def basicAuth = ef.getFullCredential(credentialName: credentialName)?.credential
    def username = basicAuth.userName
    def password = basicAuth.password
    if (!username) {
      handleError(ef, "Username is not provided for the Basic Authorization")
    }
    headers.Authorization = "Basic " + (basicAuth.userName + ':' + basicAuth.password).bytes.encodeBase64()
    println "Setting Basic Auth: username $basicAuth.userName"
    if (meta.checkConnectionUri != null) {
        uri.path = augmentUri(uri.path, meta.checkConnectionUri)
        uri.query = fetchQuery(meta.checkConnectionUri)
        println "Check Connection URI: $uri"
        uriChanged = true
    }
  }

  if (authType == "bearerToken") {
    def meta = checkConnectionMeta?.authSchemes?.bearerToken
    def credentialName = meta?.credentialName ?: 'bearer_credential'
    def bearer = ef.getFullCredential(credentialName: credentialName)?.credential
    def prefix = meta.prefix ?: "Bearer"
    headers.Authorization = prefix + " " + bearer.password
    println "Setting Bearer Auth with prefix $prefix"
    if (meta.checkConnectionUri != null) {
        uri.path = augmentUri(uri.path, meta.checkConnectionUri)
        uri.query = fetchQuery(meta.checkConnectionUri)
        println "Check Connection URI: $uri"
        uriChanged = true
    }
  }

  if (authType == "anonymous") {
    println "Anonymous access"
    def meta = checkConnectionMeta?.authSchemes?.anonymous
    if (meta.checkConnectionUri != null) {
      uri.path = augmentUri(uri.path, meta.checkConnectionUri)
      uri.query = fetchQuery(meta.checkConnectionUri)
      println "Check Connection URI: $uri"
      uriChanged = true
    }
  }

  if (checkConnectionMeta.checkConnectionUri != null && !uriChanged) {
    uri.path = augmentUri(uri.path, checkConnectionMeta.checkConnectionUri)
    uri.query = fetchQuery(checkConnectionMeta.checkConnectionUri)
    println "URI: $uri"
  }

  response.success = { resp, reader ->
    assert resp.status == 200
    println "Status Line: ${resp.statusLine}"
    println "Response length: ${resp.headers.'Content-Length'}"
    System.out << reader // print response reader
  }

  response.failure = { resp, reader ->
    println "Check connection failed"
    String status = resp.statusLine.toString()
    println "$status"
    String body =  reader.text
    println body
    String message = "Check Connection Failed: ${status}, $body"
    handleError(ef, message)
  }
}

def handleError(def ef, def message) {
  ef.setProperty(propertyName: "/myJobStep/summary", value: message)
  ef.setProperty(propertyName: "/myJob/configError", value: message)
  System.exit(-1)
}

def fetchQuery(String uri) {
  def parts = uri.split(/\?/)
  def query = [:]
  if (parts.size() > 1) {
    def queryString = parts[1]
    queryString.split('&').each {
      def p = it.split('=')
      if (p.size() > 1) {
        query.put(p[0], p[1])
      }
    }
  }
  println "Query: $query"
  return query
}

def augmentUri(path, uri) {
    uri = uri.split(/\?/).getAt(0)
    p = path + uri
    p = p.replaceAll(/\/+/, '/')
    return p
}
// DO NOT EDIT THIS BLOCK ABOVE ^^^=== check_connection ends, checksum: dbda5b9891e7e1de0a743ee5faf2b7a8 ===
