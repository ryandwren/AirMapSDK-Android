package com.airmap.airmapsdk.networking.services;

import androidx.annotation.Nullable;
import android.text.TextUtils;

import com.airmap.airmapsdk.AirMapException;
import com.airmap.airmapsdk.models.Coordinate;
import com.airmap.airmapsdk.models.flight.AirMapEvaluation;
import com.airmap.airmapsdk.models.rules.AirMapJurisdiction;
import com.airmap.airmapsdk.models.rules.AirMapRuleset;
import com.airmap.airmapsdk.models.shapes.AirMapGeometry;
import com.airmap.airmapsdk.models.shapes.AirMapPolygon;
import com.airmap.airmapsdk.models.status.AirMapAirspaceStatus;
import com.airmap.airmapsdk.networking.callbacks.AirMapCallback;
import com.airmap.airmapsdk.networking.callbacks.GenericListOkHttpCallback;
import com.airmap.airmapsdk.networking.callbacks.GenericOkHttpCallback;
import com.airmap.airmapsdk.util.Utils;
import com.mapbox.mapboxsdk.geometry.LatLng;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;

public class RulesetService extends BaseService {

    static Call getJurisdictions(LatLng southwest, LatLng northeast, final AirMapCallback<List<AirMapJurisdiction>> callback) {
        Map<String, String> params = new HashMap<>();
        params.put("bounds", southwest.getLatitude() + "," + southwest.getLongitude() + "," + northeast.getLatitude() + "," + northeast.getLongitude());

        return AirMap.getClient().get(rulesetBaseUrl, params, new GenericListOkHttpCallback(new AirMapCallback<List<AirMapRuleset>>() {
            @Override
            protected void onSuccess(List<AirMapRuleset> rulesets) {
                // convert rulesets to jurisdictions
                List<AirMapJurisdiction> jurisdictions = new ArrayList<>();

                for (AirMapRuleset ruleset : rulesets) {
                    AirMapJurisdiction jurisdiction;
                    if (jurisdictions.contains(ruleset.getJurisdiction())) {
                        jurisdiction = jurisdictions.get(jurisdictions.indexOf(ruleset.getJurisdiction()));
                    } else {
                        jurisdiction = ruleset.getJurisdiction();
                        jurisdictions.add(jurisdiction);
                    }

                    jurisdiction.addRuleset(ruleset);
                }

                callback.success(jurisdictions);
            }

            @Override
            protected void onError(AirMapException e) {
                callback.error(e);
            }
        }, AirMapRuleset.class));
    }

    static Call getJurisdictions(JSONObject geometry, @Nullable String accessToken, final AirMapCallback<List<AirMapJurisdiction>> callback) {
        Map<String, Object> params = new HashMap<>();
        params.put("geometry", geometry);

        String url = rulesetBaseUrl;
        if (!TextUtils.isEmpty(accessToken)) {
            url += "?access_token=" + accessToken;
        }

        return AirMap.getClient().postWithJsonBody(url, params, new GenericListOkHttpCallback(new AirMapCallback<List<AirMapRuleset>>() {
            @Override
            protected void onSuccess(List<AirMapRuleset> rulesets) {
                // convert rulesets to jurisdictions
                List<AirMapJurisdiction> jurisdictions = new ArrayList<>();

                for (AirMapRuleset ruleset : rulesets) {
                    AirMapJurisdiction jurisdiction;
                    if (jurisdictions.contains(ruleset.getJurisdiction())) {
                        jurisdiction = jurisdictions.get(jurisdictions.indexOf(ruleset.getJurisdiction()));
                    } else {
                        jurisdiction = ruleset.getJurisdiction();
                        jurisdictions.add(jurisdiction);
                    }

                    jurisdiction.addRuleset(ruleset);
                }

                callback.success(jurisdictions);
            }

            @Override
            protected void onError(AirMapException e) {
                callback.error(e);
            }
        }, AirMapRuleset.class));
    }

    static Call getRulesets(Coordinate coordinate, @Nullable String accessToken, AirMapCallback<List<AirMapRuleset>> listener) {
        Map<String, Object> params = new HashMap<>();
        params.put("latitude", String.valueOf(coordinate.getLatitude()));
        params.put("longitude", String.valueOf(coordinate.getLongitude()));
        String url = rulesetBaseUrl;
        if (!TextUtils.isEmpty(accessToken)) {
            url += "?access_token=" + accessToken;
        }
        return AirMap.getClient().postWithJsonBody(url, params, new GenericListOkHttpCallback(listener, AirMapRuleset.class));
    }

    static Call getRulesets(JSONObject geometry, @Nullable String accessToken, AirMapCallback<List<AirMapRuleset>> listener) {
        Map<String, Object> params = new HashMap<>();
        params.put("geometry", geometry);
        String url = rulesetBaseUrl;
        if (!TextUtils.isEmpty(accessToken)) {
            url += "?access_token=" + accessToken;
        }
        return AirMap.getClient().postWithJsonBody(url, params, new GenericListOkHttpCallback(listener, AirMapRuleset.class));
    }

    static Call getRulesets(List<String> rulesetIds, @Nullable String accessToken, AirMapCallback<List<AirMapRuleset>> listener) {
        Map<String, String> params = new HashMap<>();
        params.put("rulesets", TextUtils.join(",",rulesetIds));
        String url = rulesetBaseUrl;
        if (!TextUtils.isEmpty(accessToken)) {
            url += "?access_token=" + accessToken;
        }
        return AirMap.getClient().get(url, params, new GenericListOkHttpCallback(listener, AirMapRuleset.class));
    }

    static Call getRuleset(String rulesetId, @Nullable String accessToken, AirMapCallback<AirMapRuleset> callback) {
        String url = String.format(rulesetByIdUrl, rulesetId);
        if (!TextUtils.isEmpty(accessToken)) {
            url += "?access_token=" + accessToken;
        }
        return AirMap.getClient().get(url, new GenericOkHttpCallback(callback, AirMapRuleset.class));
    }

    static Call getRules(String rulesetId, @Nullable String accessToken, AirMapCallback<AirMapRuleset> listener) {
        String url = String.format(rulesByIdUrl, rulesetId);
        if (!TextUtils.isEmpty(accessToken)) {
            url += "?access_token=" + accessToken;
        }
        return AirMap.getClient().get(url, new GenericOkHttpCallback(listener, AirMapRuleset.class));
    }

    static Call getAdvisories(List<String> rulesets, List<Coordinate> geometry, @Nullable Date start, @Nullable Date end, @Nullable Map<String,Object> flightFeatures, @Nullable String accessToken, AirMapCallback<AirMapAirspaceStatus> listener) {
        Map<String, Object> params = new HashMap<>();
        params.put("rulesets", TextUtils.join(",", rulesets));
        params.put("geometry", "POLYGON(" + Utils.makeGeoString(geometry) + ")");

        if (start != null) {
            params.put("start", Utils.getIso8601StringFromDate(start));
        }

        if (end != null) {
            params.put("end", Utils.getIso8601StringFromDate(end));
        }

        if (flightFeatures != null && !flightFeatures.isEmpty()) {
            try {
                JSONObject jsonObject = new JSONObject();
                for (String key : flightFeatures.keySet()) {
                    jsonObject.put(key, flightFeatures.get(key));
                }
                params.put("flight_features", jsonObject);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        String url = advisoriesUrl;
        if (!TextUtils.isEmpty(accessToken)) {
            url += "?access_token=" + accessToken;
        }
        return AirMap.getClient().postWithJsonBody(url, params, new GenericOkHttpCallback(listener, AirMapAirspaceStatus.class));
    }

    static Call getAdvisories(List<String> rulesets, AirMapPolygon polygon, @Nullable Date start, @Nullable Date end, @Nullable Map<String,Object> flightFeatures, @Nullable String accessToken, AirMapCallback<AirMapAirspaceStatus> listener) {
        return getAdvisories(rulesets, AirMapGeometry.getGeoJSONFromGeometry(polygon), start, end, flightFeatures, accessToken, listener);
    }

    static Call getAdvisories(List<String> rulesets, JSONObject geometry, @Nullable Date start, @Nullable Date end, @Nullable Map<String,Object> flightFeatures, @Nullable String accessToken, AirMapCallback<AirMapAirspaceStatus> listener) {
        Map<String, Object> params = new HashMap<>();
        params.put("rulesets", TextUtils.join(",", rulesets));
        params.put("geometry", geometry);

        if (start != null) {
            params.put("start", Utils.getIso8601StringFromDate(start));
        }

        if (end != null) {
            params.put("end", Utils.getIso8601StringFromDate(end));
        }

        if (flightFeatures != null && !flightFeatures.isEmpty()) {
            try {
                JSONObject jsonObject = new JSONObject();
                for (String key : flightFeatures.keySet()) {
                    jsonObject.put(key, flightFeatures.get(key));
                }
                params.put("flight_features", jsonObject);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        String url = advisoriesUrl;
        if (!TextUtils.isEmpty(accessToken)) {
            url += "?access_token=" + accessToken;
        }
        return AirMap.getClient().postWithJsonBody(url, params, new GenericOkHttpCallback(listener, AirMapAirspaceStatus.class));
    }

    static Call getEvaluation(List<String> rulesets, JSONObject geometry, @Nullable String accessToken, AirMapCallback<AirMapEvaluation> callback) {
        Map<String, Object> params = new HashMap<>();
        params.put("rulesets", TextUtils.join(",", rulesets));
        params.put("geometry", geometry);
        params.put("with_markdown", true);
        String url = evaluationUrl;
        if (!TextUtils.isEmpty(accessToken)) {
            url += "?access_token=" + accessToken;
        }
        return AirMap.getClient().postWithJsonBody(url, params, new GenericOkHttpCallback(callback, AirMapEvaluation.class));
    }
}
