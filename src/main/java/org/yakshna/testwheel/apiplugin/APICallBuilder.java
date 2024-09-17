package org.yakshna.testwheel.apiplugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.jenkinsci.Symbol;
import org.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import jenkins.tasks.SimpleBuildStep;

public class APICallBuilder extends Builder implements SimpleBuildStep {

	private final String apiUrl;
	
	static final String STATUS = "status";

	@DataBoundConstructor
	public APICallBuilder(String apiUrl) {
		this.apiUrl = apiUrl;
	}

	public String getApiUrl() {
		return apiUrl;
	}

	@SuppressWarnings("deprecation")
	@SuppressFBWarnings("REC_CATCH_EXCEPTION")
	@Override
	public void perform(Run<?, ?> run, FilePath workspace, EnvVars env, Launcher launcher, TaskListener listener) {
		try (CloseableHttpClient client = HttpClients.createDefault()) {
			listener.getLogger().println("Calling API: " + apiUrl);
			HttpUriRequestBase request = new HttpGet(apiUrl);
			try (CloseableHttpResponse response = client.execute(request)) {
				if (response.getCode() == 201) {
					String responseBody = EntityUtils.toString(response.getEntity());
					JSONObject jsonResponse = new JSONObject(responseBody);
					if ("success".equalsIgnoreCase(jsonResponse.getString(STATUS))) {
						String runId = jsonResponse.getString("output");
						if (runId != null && !runId.isEmpty()) {
							String secondUrl = apiUrl + "&runId=" + runId;
							listener.getLogger().println("Polling API: " + secondUrl);
							while (true) {
								HttpUriRequestBase secondRequest = new HttpGet(secondUrl);
								try (CloseableHttpResponse secondResponse = client.execute(secondRequest)) {
									String secondResponseBody = EntityUtils.toString(secondResponse.getEntity());
									JSONObject secondJsonResponse = new JSONObject(secondResponseBody);
									if ("SUCCESS".equalsIgnoreCase(secondJsonResponse.getString(STATUS))) {
										String reportUrl = secondJsonResponse.getString("output");
										listener.getLogger().println("Downloading report from: " + reportUrl);
										HttpUriRequestBase reportRequest = new HttpGet(reportUrl);
										try (CloseableHttpResponse reportResponse = client.execute(reportRequest);
												InputStream reportStream = reportResponse.getEntity().getContent()) {
											File reportFile = new File(workspace.getRemote(), "report.pdf");
											try (FileOutputStream fos = new FileOutputStream(reportFile)) {
												byte[] buffer = new byte[1024];
												int len;
												while ((len = reportStream.read(buffer)) != -1) {
													fos.write(buffer, 0, len);
												}
											}
											listener.getLogger().println(
													"Report downloaded successfully: " + reportFile.getAbsolutePath());
											run.setResult(Result.SUCCESS);
											return;
										}
									} else if ("FAILURE".equalsIgnoreCase(secondJsonResponse.getString(STATUS))) {
										listener.getLogger().println("API Test failed");
										run.setResult(Result.FAILURE);
										return;
									}
									Thread.sleep(20000); 
								} catch (ParseException e) {
									listener.getLogger().println("API Test failed");
									run.setResult(Result.FAILURE);
									return;
								}
							}
						} else {
							listener.getLogger().println("Output not found in the response");
							run.setResult(Result.FAILURE);
						}
					} else {
						listener.getLogger().println("API Request failed. Please Check the API URL");
						run.setResult(Result.FAILURE);
					}
				} else {
					listener.getLogger().println("API Request failed. Status: " + response.getCode());
					run.setResult(Result.FAILURE);
				}
			} catch (ParseException e1) {
				listener.getLogger().println("API Request failed. " + e1.getMessage());
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
	@Symbol("apiCallBuilder")
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

		@SuppressWarnings("rawtypes")
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return "APICallBuilder";
		}
	}
}
