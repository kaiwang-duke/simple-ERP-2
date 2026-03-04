package com.nineforce.service.tool;

import com.google.api.services.sqladmin.SQLAdmin;
import com.google.api.services.sqladmin.model.DatabaseInstance;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.http.HttpCredentialsAdapter;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class CloudSqlService {

    // duplicate declaration due module constrain.This also in main-app module
    public static final String PROJECT_ID = "eng-empire-470108-k1";
    private final SQLAdmin sqlAdminService;

    public CloudSqlService() throws IOException {
        // Initialize SQLAdmin service
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
        sqlAdminService = new SQLAdmin.Builder(
                new com.google.api.client.http.javanet.NetHttpTransport(),
                com.google.api.client.json.gson.GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("Google-SQLAdminSample/0.1")
                .build();
    }

    public DatabaseInstance getInstanceStatus(String instanceId) throws IOException {
        SQLAdmin.Instances.Get getRequest = sqlAdminService.instances().get(PROJECT_ID, instanceId);
        return getRequest.execute();
    }

    public void startInstance(String instanceId) throws IOException {
        DatabaseInstance instance = getInstanceStatus(instanceId);
        instance.getSettings().setActivationPolicy("ALWAYS");

        SQLAdmin.Instances.Patch patchRequest = sqlAdminService.instances().patch(PROJECT_ID, instanceId, instance);
        patchRequest.execute();
    }

    public void stopInstance(String instanceId) throws IOException {
        DatabaseInstance instance = getInstanceStatus(instanceId);
        instance.getSettings().setActivationPolicy("NEVER");

        SQLAdmin.Instances.Patch patchRequest = sqlAdminService.instances().patch(PROJECT_ID, instanceId, instance);
        patchRequest.execute();
    }
}