package au.com.dius.pact.provider.broker

import au.com.dius.pact.provider.ConsumerInfo
import groovy.json.JsonSlurper
import groovy.transform.Canonical
import groovyx.net.http.HTTPBuilder

import static groovyx.net.http.ContentType.JSON
import static groovyx.net.http.Method.PUT

/**
 * Client for the pact broker service
 */
@Canonical
class PactBrokerClient {

  String pactBrokerUrl
  Map options = [:]

  List fetchConsumers(String provider) {
    List consumers = []

    HalClient halClient = newHalClient()
    halClient.navigate('pb:latest-provider-pacts', provider: provider).pacts { pact ->
      consumers << new ConsumerInfo(pact.name, new URL(pact.href))
      if (options.authentication) {
        consumers.last().pactFileAuthentication = options.authentication
      }
    }

    consumers
  }

  private newHalClient() {
    new HalClient(pactBrokerUrl, options)
  }

  def uploadPactFile(File pactFile, String version) {
    def pact = new JsonSlurper().parse(pactFile)
    def http = new HTTPBuilder(pactBrokerUrl)
    def proxyHost = System.getProperty('PACT_BROKER_PROXY_HOST')
    def proxyPort = System.getProperty('PACT_BROKER_PROXY_PORT')
    def proxyScheme = System.getProperty('PACT_BROKER_PROXY_SCHEME')
    def brokerUser = System.getProperty('PACT_BROKER_USERNAME')
    def brokerPassword = System.getProperty('PACT_BROKER_PASSWORD')

    if (proxyHost != null &&  proxyPort != null && proxyScheme != null) {
      http.setProxy(proxyHost, Integer.valueOf( proxyPort), proxyScheme)
    }

    if (brokerUser != null && brokerPassword != null) {
      http.auth.basic(brokerUser, brokerPassword)
    }

    http.parser.'application/hal+json' = http.parser.'application/json'
    http.request(PUT, JSON) {
      uri.path = "/pacts/provider/${pact.provider.name}/consumer/${pact.consumer.name}/version/$version"
      requestContentType = JSON
      body = pactFile.text

      response.success = { resp -> resp.statusLine as String }

      response.failure = { resp, json ->
        def error = json?.errors?.join(', ') ?: 'Unknown error'
        "FAILED! ${resp.statusLine.statusCode} ${resp.statusLine.reasonPhrase} - ${error}"
      }
    }
  }
}
