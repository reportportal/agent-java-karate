package com.epam.reportportal.karate;

import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import com.intuit.karate.core.FeatureResult;
import io.reactivex.Maybe;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.util.reflection.FieldSetter;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ReportPortal.class, Launch.class})
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})
public class ReportPortalPublisherTest {

    private ReportPortalPublisher reportPortalPublisher;
    private Launch launch;

    @Before
    public void setUp() {
        launch = mockLaunch();
        reportPortalPublisher = new ReportPortalPublisher(() -> launch);
    }

    @Test
    public void shouldStartLaunch() {
        reportPortalPublisher.startLaunch();
        verify(launch, times(1)).start();
    }

    @Test
    public void shouldFinishLaunch() {
        reportPortalPublisher.finishLaunch();
        verify(launch, times(1)).finish(any(FinishExecutionRQ.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldStartFeature() {
        FeatureResult featureResult = mock(FeatureResult.class);
        when(featureResult.getCallNameForReport()).thenReturn("featureName");
        when(launch.startTestItem(any(StartTestItemRQ.class))).thenReturn(mock(Maybe.class));
        reportPortalPublisher.startFeature(featureResult);
        verify(launch, times(1)).startTestItem(any(StartTestItemRQ.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    @SneakyThrows
    public void shouldFinishFeature() {
        FeatureResult featureResult = mock(FeatureResult.class);
        when(featureResult.getCallNameForReport()).thenReturn("featureName");
        ConcurrentHashMap<String, Maybe<String>> featureIdMap = new ConcurrentHashMap<>();
        featureIdMap.put("featureName", mock(Maybe.class));
        FieldSetter.setField(reportPortalPublisher, ReportPortalPublisher.class.getDeclaredField("featureIdMap"), featureIdMap);
        reportPortalPublisher.finishFeature(featureResult);
        verify(launch, times(1)).finishTestItem(any(Maybe.class), any(FinishTestItemRQ.class));
    }

    @SneakyThrows
    private Launch mockLaunch() {
        PowerMockito.mockStatic(ReportPortal.class);
        ReportPortal.Builder builder = mock(ReportPortal.Builder.class);
        Launch launch = mock(Launch.class);
        ReportPortal reportPortal = mock(ReportPortal.class);
        PowerMockito.when(ReportPortal.class, "builder").thenReturn(builder);
        when(builder.build()).thenReturn(reportPortal);
        when(reportPortal.getParameters()).thenReturn(getListenerParameters());
        when(launch.getParameters()).thenReturn(getListenerParameters());
        when(reportPortal.newLaunch(any(StartLaunchRQ.class))).thenReturn(launch);
        return launch;
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
