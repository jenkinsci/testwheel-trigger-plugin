package org.yakshna.testwheel.apiplugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.jenkinsci.Symbol;
import org.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.ProxyConfiguration;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;

public class TestWheelTrigger extends Builder implements SimpleBuildStep {

	private final Secret apiKey;
	private final Secret prjctKey;

	private static String url = "https://app.testwheel.com/test-appln";

	private static final String STATUS = "status";

	@DataBoundConstructor
	public TestWheelTrigger(Secret apiKey, Secret prjctKey) {
		this.apiKey = apiKey;
		this.prjctKey = prjctKey;
	}

	public Secret getApiKey() {
		return apiKey;
	}

	public Secret getPrjctKey() {
		return prjctKey;
	}

	@Override
	public void perform(Run<?, ?> run, FilePath workspace, EnvVars env, Launcher launcher, TaskListener listener) {
		try (CloseableHttpClient client = createHttpClientWithProxy()) {
			JSONObject requestBody = new JSONObject();
			// lgtm[jenkins/plaintext-storage]
			String decryptedApiKey = apiKey.getPlainText();
			String decryptedPrjctKey = prjctKey.getPlainText();
			requestBody.put("apiKey", decryptedApiKey);
			requestBody.put("prjctKey", decryptedPrjctKey);
			HttpPost request = new HttpPost(url); // Change to POST
			request.setHeader("Content-Type", "application/json");
			request.setEntity(new StringEntity(requestBody.toString()));
			HttpClientResponseHandler<String> responseHandler = new HttpClientResponseHandler<String>() {
				@Override
				public String handleResponse(ClassicHttpResponse response) throws HttpException, IOException {
					return EntityUtils.toString(response.getEntity());
				}
			};
			String responseBody = client.execute(request, responseHandler);
			JSONObject jsonResponse = new JSONObject(responseBody);
			if ("success".equalsIgnoreCase(jsonResponse.getString(STATUS))) {
				String runId = jsonResponse.getString("output");
				if (runId != null && !runId.isEmpty()) {
					JSONObject secondRequestBody = new JSONObject();
					secondRequestBody.put("apiKey", decryptedApiKey);
					secondRequestBody.put("prjctKey", decryptedPrjctKey);
					secondRequestBody.put("runId", runId);
					HttpPost secondRequest = new HttpPost(url);
					secondRequest.setHeader("Content-Type", "application/json");
					secondRequest.setEntity(new StringEntity(secondRequestBody.toString()));
					while (true) {
						String secondResponseBody = client.execute(secondRequest, responseHandler);
						JSONObject secondJsonResponse = new JSONObject(secondResponseBody);
						if ("SUCCESS".equalsIgnoreCase(secondJsonResponse.getString(STATUS))) {
							String reportUrl = secondJsonResponse.getString("output");
							listener.getLogger().println("Downloading report from: " + reportUrl);
							HttpGet reportRequest = new HttpGet(reportUrl);
							HttpClientResponseHandler<InputStream> reportResponseHandler = new HttpClientResponseHandler<InputStream>() {
								@Override
								public InputStream handleResponse(ClassicHttpResponse response)
										throws HttpException, IOException {
									return response.getEntity().getContent();
								}
							};
							InputStream reportStream = client.execute(reportRequest, reportResponseHandler);
							FilePath reportFilePath = workspace.child("report.pdf");
							try (OutputStream fos = reportFilePath.write()) {
								byte[] buffer = new byte[1024];
								int len;
								while ((len = reportStream.read(buffer)) != -1) {
									fos.write(buffer, 0, len);
								}
							}
							listener.getLogger()
									.println("Report downloaded successfully: " + reportFilePath.getRemote());
							run.setResult(Result.SUCCESS);
							return;
						} else if ("FAILURE".equalsIgnoreCase(secondJsonResponse.getString(STATUS))) {
							listener.getLogger().println("API Test failed");
							run.setResult(Result.FAILURE);
							return;
						}
						Thread.sleep(20000); // Wait before retrying
					}
				} else {
					listener.getLogger().println("Output not found in the response");
					run.setResult(Result.FAILURE);
				}
			} else {
				listener.getLogger().println("API Request failed. Please Check the API URL");
				run.setResult(Result.FAILURE);
			}
		} catch (IOException e) {
			listener.getLogger().println("Error: " + e.getMessage());
			run.setResult(Result.FAILURE);
		} catch (InterruptedException e) {
			listener.getLogger().println("InterruptedException occurred: " + e.getMessage());
			run.setResult(Result.FAILURE);
			Thread.currentThread().interrupt();
		}
	}

	@Extension
	@Symbol("testwheelTrigger")
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

		@SuppressWarnings("rawtypes")
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return "TestwheelTrigger";
		}
	}
	
	private CloseableHttpClient createHttpClientWithProxy() {
	    Jenkins jenkins = Jenkins.get();
	    ProxyConfiguration proxyConfig = jenkins.proxy;

	    if (proxyConfig != null) {
	    	HttpHost proxy = new HttpHost(proxyConfig.name, proxyConfig.port);

	    	BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
	        if (proxyConfig.getUserName() != null && proxyConfig.getSecretPassword() != null) {
	            String decryptedPassword = Secret.toString(proxyConfig.getSecretPassword());
	            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(
	                    proxyConfig.getUserName(), decryptedPassword.toCharArray());
	            credentialsProvider.setCredentials(new AuthScope(proxyConfig.name, proxyConfig.port), credentials);
	        }
	        return HttpClients.custom()
	                .setProxy(proxy)
	                .setDefaultCredentialsProvider(credentialsProvider)
	                .build();
	    }
	    return HttpClients.createDefault();
	}
}
