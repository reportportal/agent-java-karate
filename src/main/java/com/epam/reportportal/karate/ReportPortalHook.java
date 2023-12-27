/*
 *  Copyright 2023 EPAM Systems
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.epam.reportportal.karate;

import com.epam.reportportal.service.ReportPortal;
import com.intuit.karate.RuntimeHook;
import com.intuit.karate.Suite;
import com.intuit.karate.core.FeatureRuntime;
import com.intuit.karate.core.ScenarioRuntime;
import com.intuit.karate.core.Step;
import com.intuit.karate.core.StepResult;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.http.Response;

public class ReportPortalHook implements RuntimeHook {
	public ReportPortalHook(ReportPortal reportPortal) {

	}

	public ReportPortalHook() {
		this(ReportPortal.builder().build());
	}

	@Override
	public boolean beforeScenario(ScenarioRuntime sr) {
		return RuntimeHook.super.beforeScenario(sr);
	}

	@Override
	public void afterScenario(ScenarioRuntime sr) {
		RuntimeHook.super.afterScenario(sr);
	}

	@Override
	public boolean beforeFeature(FeatureRuntime fr) {
		return RuntimeHook.super.beforeFeature(fr);
	}

	@Override
	public void afterFeature(FeatureRuntime fr) {
		RuntimeHook.super.afterFeature(fr);
	}

	@Override
	public void beforeSuite(Suite suite) {
		RuntimeHook.super.beforeSuite(suite);
	}

	@Override
	public void afterSuite(Suite suite) {
		RuntimeHook.super.afterSuite(suite);
	}

	@Override
	public boolean beforeStep(Step step, ScenarioRuntime sr) {
		return RuntimeHook.super.beforeStep(step, sr);
	}

	@Override
	public void afterStep(StepResult result, ScenarioRuntime sr) {
		RuntimeHook.super.afterStep(result, sr);
	}

	@Override
	public void beforeHttpCall(HttpRequest request, ScenarioRuntime sr) {
		RuntimeHook.super.beforeHttpCall(request, sr);
	}

	@Override
	public void afterHttpCall(HttpRequest request, Response response, ScenarioRuntime sr) {
		RuntimeHook.super.afterHttpCall(request, response, sr);
	}
}
