/*
 * Copyright 2024 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.reportportal.karate;

import com.epam.reportportal.karate.utils.ReflectUtils;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.Launch;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureResult;
import com.intuit.karate.resource.Resource;
import io.reactivex.Maybe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReportPortalPublisherTest {
    @Mock
    Launch launchMock;
    private ReportPortalPublisher reportPortalPublisher;

    @BeforeEach
    public void setUp() {
        reportPortalPublisher = new ReportPortalPublisher(() -> launchMock);
    }

    @Test
    public void shouldStartLaunch() {
        reportPortalPublisher.startLaunch();
        verify(launchMock, times(1)).start();
    }

    @Test
    public void shouldFinishLaunch() {
        when(launchMock.getParameters()).thenReturn(getListenerParameters());
        reportPortalPublisher.finishLaunch();
        verify(launchMock, times(1)).finish(any(FinishExecutionRQ.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldStartFeature() throws URISyntaxException {
        FeatureResult featureResult = mock(FeatureResult.class);
        Feature feature = mock(Feature.class);
        Resource resource = mock(Resource.class);
        when(featureResult.getFeature()).thenReturn(feature);
        when(featureResult.getCallNameForReport()).thenReturn("featureName");
        when(feature.getResource()).thenReturn(resource);
        when(resource.getUri()).thenReturn(new URI("file:///feature/simple.feature"));
        when(launchMock.startTestItem(any(StartTestItemRQ.class))).thenReturn(mock(Maybe.class));
        reportPortalPublisher.startFeature(featureResult);
        verify(launchMock, times(1)).startTestItem(any(StartTestItemRQ.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldFinishFeature() throws NoSuchFieldException {
        FeatureResult featureResult = mock(FeatureResult.class);
        when(featureResult.getCallNameForReport()).thenReturn("featureName");
        ConcurrentHashMap<String, Maybe<String>> featureIdMap = new ConcurrentHashMap<>();
        featureIdMap.put("featureName", mock(Maybe.class));
        ReflectUtils.setField(reportPortalPublisher, ReportPortalPublisher.class.getDeclaredField("featureIdMap"), featureIdMap);
        reportPortalPublisher.finishFeature(featureResult);
        verify(launchMock, times(1)).finishTestItem(any(Maybe.class), any(FinishTestItemRQ.class));
    }

    private ListenerParameters getListenerParameters() {
        ListenerParameters parameters = new ListenerParameters();
        parameters.setLaunchName("launch");
        parameters.setBaseUrl("url");
        parameters.setProjectName("project");
        System.setProperty("rp.launch.id", "launchId");
        return parameters;
    }
}
