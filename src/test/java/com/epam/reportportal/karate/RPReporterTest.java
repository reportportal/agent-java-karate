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
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.FieldSetter;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ReportPortal.class, Launch.class})
public class RPReporterTest {

    private RPReporter rpReporter;
    private Launch launch;

    @Before
    public void setUp() {
        launch = mockLaunch();
        rpReporter = new RPReporter(() -> launch);
    }

    @Test
    public void shouldStartLaunch() {
        rpReporter.startLaunch();
        verify(launch, times(1)).start();
    }

    @Test
    public void shouldFinishLaunch() {
        rpReporter.finishLaunch();
        verify(launch, times(1)).finish(any(FinishExecutionRQ.class));
    }

    @Test
    public void shouldStartFeature() {
        FeatureResult featureResult = mock(FeatureResult.class);
        when(featureResult.getCallName()).thenReturn("featureName");
        when(launch.startTestItem(any(StartTestItemRQ.class))).thenReturn(mock(Maybe.class));
        rpReporter.startFeature(featureResult);
        verify(launch, times(1)).startTestItem(any(StartTestItemRQ.class));
    }

    @Test
    @SneakyThrows
    public void shouldFinishFeature() {
        FeatureResult featureResult = mock(FeatureResult.class);
        when(featureResult.getCallName()).thenReturn("featureName");
        ConcurrentHashMap<String, Maybe<String>> featureIdMap = new ConcurrentHashMap<>();
        featureIdMap.put("featureName", mock(Maybe.class));
        new FieldSetter(rpReporter, RPReporter.class.getDeclaredField("featureIdMap")).set(featureIdMap);
        rpReporter.finishFeature(featureResult);
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
