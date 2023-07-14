package com.epam.reportportal.karate;

import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.utils.ReflectUtils;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.intuit.karate.core.FeatureResult;
import io.reactivex.Maybe;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
//import org.powermock.core.classloader.annotations.PrepareForTest;

import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
//@PrepareForTest({ReportPortal.class, Launch.class})
public class ReportPortalPublisherTest {
    private ReportPortalPublisher reportPortalPublisher;
    @Mock
    Launch launchMock;

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
    public void shouldStartFeature() {
        FeatureResult featureResult = mock(FeatureResult.class);
        when(featureResult.getCallNameForReport()).thenReturn("featureName");
        when(launchMock.startTestItem(any(StartTestItemRQ.class))).thenReturn(mock(Maybe.class));
        reportPortalPublisher.startFeature(featureResult);
        verify(launchMock, times(1)).startTestItem(any(StartTestItemRQ.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    @SneakyThrows
    public void shouldFinishFeature() {
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
