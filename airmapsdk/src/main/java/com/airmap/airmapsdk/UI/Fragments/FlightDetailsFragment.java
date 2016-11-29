package com.airmap.airmapsdk.ui.fragments;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.airmap.airmapsdk.AirMapException;
import com.airmap.airmapsdk.Utils;
import com.airmap.airmapsdk.models.aircraft.AirMapAircraft;
import com.airmap.airmapsdk.models.aircraft.AirMapAircraftManufacturer;
import com.airmap.airmapsdk.models.aircraft.AirMapAircraftModel;
import com.airmap.airmapsdk.models.airspace.AirMapAirspace;
import com.airmap.airmapsdk.models.flight.AirMapFlight;
import com.airmap.airmapsdk.models.permits.AirMapAvailablePermit;
import com.airmap.airmapsdk.models.permits.AirMapPilotPermit;
import com.airmap.airmapsdk.models.pilot.AirMapPilot;
import com.airmap.airmapsdk.models.shapes.AirMapGeometry;
import com.airmap.airmapsdk.models.shapes.AirMapPolygon;
import com.airmap.airmapsdk.models.status.AirMapStatus;
import com.airmap.airmapsdk.models.status.AirMapStatusAdvisory;
import com.airmap.airmapsdk.networking.callbacks.AirMapCallback;
import com.airmap.airmapsdk.networking.services.AirMap;
import com.airmap.airmapsdk.R;
import com.airmap.airmapsdk.networking.services.AirspaceService;
import com.airmap.airmapsdk.ui.activities.CreateEditAircraftActivity;
import com.airmap.airmapsdk.ui.activities.CreateFlightActivity;
import com.airmap.airmapsdk.ui.activities.ProfileActivity;
import com.airmap.airmapsdk.ui.adapters.AircraftAdapter;
import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.annotations.Polygon;
import com.mapbox.mapboxsdk.annotations.PolygonOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.airmap.airmapsdk.Utils.getAltitudePresets;
import static com.airmap.airmapsdk.Utils.getCirclePolygon;
import static com.airmap.airmapsdk.Utils.getDurationPresets;
import static com.airmap.airmapsdk.Utils.getBufferPresets;
import static com.airmap.airmapsdk.Utils.getStatusCircleColor;
import static com.airmap.airmapsdk.Utils.indexOfDurationPreset;
import static com.airmap.airmapsdk.Utils.indexOfMeterPreset;

public class FlightDetailsFragment extends Fragment implements OnMapReadyCallback {

    private static final String TAG = "FlightDetailsFragment";

    private static final int REQUEST_CREATE_AIRCRAFT = 1;

    private OnFragmentInteractionListener mListener;
    private MapboxMap map;

    //Views
    private MapView mapView;
    private TextView radiusValueTextView;
    private SeekBar radiusSeekBar;
    private TextView altitudeValueTextView;
    private SeekBar altitudeSeekBar;
    private RelativeLayout startsAtTouchTarget;
    private TextView startsAtTextView;
    private TextView durationValueTextView;
    private SeekBar durationSeekBar;
    private TextView pilotProfileTextView;
    private TextView aircraftTextView;
    private ImageView infoButton;
    private SwitchCompat shareAirMapSwitch;
    private Button saveNextButton;
    private FrameLayout progressBarContainer;
    private List<AirMapAircraft> aircraft;
    private AirMapStatus latestStatus;
    private Map<String, AirMapStatusAdvisory> permitAdvisories;
    private Map<String, Polygon> polygonMap;
    private PolygonOptions radiusPolygon;

    public FlightDetailsFragment() {
        // Required empty public constructor
    }

    public static FlightDetailsFragment newInstance() {
        return new FlightDetailsFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.airmap_fragment_flight_details, container, false);
        aircraft = new ArrayList<>();
        permitAdvisories = new HashMap<>();
        polygonMap = new HashMap<>();
        initializeViews(view);
        setupAircraftDialog();
        updateStartsAtTextView();
        setupMap(savedInstanceState);
        //SeekBars are set up once the map is set up
        setupFlightDateTimePicker();
        setupOnClickListeners();
        setupSwitches();
        updateSaveNextButtonText();
        AirMap.getPilot(new AirMapCallback<AirMapPilot>() {
            @Override
            public void onSuccess(final AirMapPilot response) {
                if (!isFragmentActive()) {
                    return;
                }

                if (mListener != null) { //Since in a callback, mListener might have been destroy
                    mListener.setPilot(response);
                }
                if (response != null && response.getFullName() != null && !response.getFullName().isEmpty()) {
                    pilotProfileTextView.post(new Runnable() {
                        @Override
                        public void run() {
                            pilotProfileTextView.setText(response.getFullName());
                            progressBarContainer.setVisibility(View.GONE);
                        }
                    });
                }
            }

            @Override
            public void onError(AirMapException e) {
                e.printStackTrace();
            }
        });
        infoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String url = "https://cdn.airmap.io/static/webviews/faq.html#let-others-know";
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(url));
                startActivity(intent);

            }
        });
        return view;
    }

    private void initializeViews(View view) {
        mapView = (MapView) view.findViewById(R.id.airmap_map);
        radiusValueTextView = (TextView) view.findViewById(R.id.radius_value);
        radiusSeekBar = (SeekBar) view.findViewById(R.id.radius_seekbar);
        altitudeValueTextView = (TextView) view.findViewById(R.id.altitude_value);
        altitudeSeekBar = (SeekBar) view.findViewById(R.id.altitude_seekbar);
        startsAtTouchTarget = (RelativeLayout) view.findViewById(R.id.date_time_picker_touch_target);
        startsAtTextView = (TextView) view.findViewById(R.id.time_value);
        durationValueTextView = (TextView) view.findViewById(R.id.duration_value);
        durationSeekBar = (SeekBar) view.findViewById(R.id.duration_seekbar);
        pilotProfileTextView = (TextView) view.findViewById(R.id.pilot_profile_text);
        aircraftTextView = (TextView) view.findViewById(R.id.aircraft_label);
        infoButton = (ImageView) view.findViewById(R.id.airmap_info_button);
        shareAirMapSwitch = (SwitchCompat) view.findViewById(R.id.share_airmap_switch);
        saveNextButton = (Button) view.findViewById(R.id.save_next_button);
        progressBarContainer = (FrameLayout) view.findViewById(R.id.progress_bar_container);
    }

    private void setupMap(Bundle savedInstanceState) {
        mapView.getMapAsync(this);
        mapView.onCreate(savedInstanceState);
    }

    @Override
    public void onMapReady(MapboxMap mapboxMap) {
        map = mapboxMap;
        LatLng position = new LatLng(mListener.getFlight().getCoordinate().getLatitude(), mListener.getFlight().getCoordinate().getLongitude());
        map.setCameraPosition(new CameraPosition.Builder().target(position).zoom(14).build());
        Icon icon = IconFactory.getInstance(getContext()).fromResource(R.drawable.airmap_flight_marker);
        map.addMarker(new MarkerOptions().position(position).icon(icon));
        mapView.post(new Runnable() {
            @Override
            public void run() {
                setupSeekBars();
            }
        });

        // draw polygons for advisories with permits (green if user has permit, yellow otherwise)
        if (latestStatus != null && latestStatus.getAdvisories() != null && !latestStatus.getAdvisories().isEmpty()) {
            Map<String, AirMapStatusAdvisory> advisoryMap = new HashMap<>();
            for (AirMapStatusAdvisory advisory : latestStatus.getAdvisories()) {
                if (advisory.getAvailablePermits() != null && !advisory.getAvailablePermits().isEmpty()) {
                    advisoryMap.put(advisory.getId(), advisory);
                } else if (advisory.getColor() == AirMapStatus.StatusColor.Red) {
                    advisoryMap.put(advisory.getId(), advisory);
                }
            }

            // check if advisories have changed
            if (!permitAdvisories.keySet().equals(advisoryMap.keySet())) {
                if (!permitAdvisories.keySet().containsAll(advisoryMap.keySet())) {
                    updatePermitPolygons(advisoryMap);
                }
            }
        }
    }

    private void setupSwitches() {
        shareAirMapSwitch.setChecked(mListener.getFlight().isPublic());
        shareAirMapSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mListener.getFlight().setPublic(isChecked);
            }
        });
    }

    private void setupOnClickListeners() {
        saveNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveNextButton.post(new Runnable() {
                    @Override
                    public void run() {
                        progressBarContainer.setVisibility(View.VISIBLE);
                    }
                });
                String text = saveNextButton.getText().toString();
                if (text.equals(getString(R.string.airmap_save))) {
                    onSaveButton();
                } else if (text.equals(getString(R.string.airmap_next))) {
                    onNextButton();
                }
            }
        });

        pilotProfileTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getContext(), ProfileActivity.class);
                if (getActivity() != null) {
                    if (getActivity().getIntent().hasExtra(CreateFlightActivity.KEY_VALUE_EXTRAS)) {
                        intent.putExtra(CreateFlightActivity.KEY_VALUE_EXTRAS, getActivity().getIntent().getSerializableExtra(CreateFlightActivity.KEY_VALUE_EXTRAS));
                    }
                }
                startActivity(intent);
            }
        });
    }

    private void setupSeekBars() {
        final int radiusIndex = indexOfMeterPreset(mListener.getFlight().getBuffer(), getBufferPresets());
        final int altitudeIndex = indexOfMeterPreset(mListener.getFlight().getMaxAltitude(), getAltitudePresets());
        final int durationIndex = indexOfDurationPreset(mListener.getFlight().getEndsAt().getTime() - mListener.getFlight().getStartsAt().getTime());
        final int animationDuration = 250;

        int radiusAnimateTo = (int) (((float) radiusIndex / getBufferPresets().length) * 100);
        ObjectAnimator radiusAnimator = ObjectAnimator.ofInt(radiusSeekBar, "progress", radiusAnimateTo);
        radiusAnimator.setDuration(animationDuration);
        radiusAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        radiusAnimator.start();
        radiusAnimator.addListener(new AnimationListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                radiusSeekBar.setOnSeekBarChangeListener(new SeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (radiusPolygon != null && map != null) {
                            map.removePolygon(radiusPolygon.getPolygon()); //TODO: Save the polygon when adding to map, don't keep calling .getPolygon()
                        }
                        int color = getStatusCircleColor(latestStatus, getContext());
                        radiusPolygon = getCirclePolygon(getBufferPresets()[seekBar.getProgress()].value.doubleValue(), mListener.getFlight().getCoordinate(), color);
                        if (map != null) {
                            map.addPolygon(radiusPolygon); //TODO: Save the polygon returned here
                        }
                        radiusValueTextView.setText(getBufferPresets()[progress].label);
                        mListener.getFlight().setBuffer(getBufferPresets()[radiusSeekBar.getProgress()].value.doubleValue());

                        // zoom in/out as radius changes
                        if (getBufferPresets()[progress].value.doubleValue() > 750) {
                            map.animateCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder().target(map.getCameraPosition().target).zoom(12.5).build()));
                        } else if (getBufferPresets()[progress].value.doubleValue() > 380) {
                            map.animateCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder().target(map.getCameraPosition().target).zoom(13).build()));
                        } else {
                            map.animateCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder().target(map.getCameraPosition().target).zoom(14).build()));
                        }
                    }
                });
                radiusSeekBar.setMax(getBufferPresets().length - 1);
                radiusSeekBar.setProgress(radiusIndex);
            }
        });

        int altitudeAnimateTo = (int) (((float) altitudeIndex / getAltitudePresets().length) * 100);
        ObjectAnimator altitudeAnimator = ObjectAnimator.ofInt(altitudeSeekBar, "progress", altitudeAnimateTo);
        altitudeAnimator.setDuration(animationDuration);
        altitudeAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        altitudeAnimator.start();
        altitudeAnimator.addListener(new AnimationListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                altitudeSeekBar.setOnSeekBarChangeListener(new SeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        altitudeValueTextView.setText(getAltitudePresets()[progress].label);
                        mListener.getFlight().setMaxAltitude(getAltitudePresets()[altitudeSeekBar.getProgress()].value.doubleValue());
                    }
                });
                altitudeSeekBar.setMax(getAltitudePresets().length - 1);
                altitudeSeekBar.setProgress(altitudeIndex);
            }
        });

        int durationAnimateTo = (int) (((float) durationIndex / getDurationPresets().length) * 100);
        ObjectAnimator durationAnimator = ObjectAnimator.ofInt(durationSeekBar, "progress", durationAnimateTo);
        durationAnimator.setDuration(animationDuration);
        durationAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        durationAnimator.start();
        durationAnimator.addListener(new AnimationListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                durationSeekBar.setOnSeekBarChangeListener(new SeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        durationValueTextView.setText(getDurationPresets()[progress].label);
                        Date endsAt = new Date(mListener.getFlight().getStartsAt().getTime() + getDurationPresets()[durationSeekBar.getProgress()].value.longValue());
                        mListener.getFlight().setEndsAt(endsAt);
                    }
                });
                durationSeekBar.setMax(getDurationPresets().length - 1);
                durationSeekBar.setProgress(durationIndex);
            }
        });
    }

    private void setupFlightDateTimePicker() {
        final Calendar flightDate = Calendar.getInstance();
        flightDate.setTime(mListener.getFlight().getStartsAt() == null ? new Date() : mListener.getFlight().getStartsAt());
        updateStartsAtTextView();
        startsAtTouchTarget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Use the currently selected time as the default values for the picker
                final int nowHour = flightDate.get(Calendar.HOUR_OF_DAY);
                final int nowMinute = flightDate.get(Calendar.MINUTE);
                final int nowYear = flightDate.get(Calendar.YEAR);
                final int nowMonth = flightDate.get(Calendar.MONTH);
                final int nowDay = flightDate.get(Calendar.DAY_OF_MONTH);
                DatePickerDialog dialog = new DatePickerDialog(getContext(), new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, final int year, final int monthOfYear, final int dayOfMonth) {
                        new TimePickerDialog(getContext(), new TimePickerDialog.OnTimeSetListener() {
                            @Override
                            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                                flightDate.set(year, monthOfYear, dayOfMonth, hourOfDay, minute);
                                mListener.getFlight().setStartsAt(flightDate.getTime());
                                Date correctedEndTime = new Date(flightDate.getTime().getTime() + getDurationPresets()[durationSeekBar.getProgress()].value.longValue());
                                mListener.getFlight().setEndsAt(correctedEndTime);
                                updateStartsAtTextView();
                                mListener.flightChanged();
                            }
                        }, nowHour, nowMinute, false).show();
                        updateSaveNextButtonText();
                    }
                }, nowYear, nowMonth, nowDay);
                Date now = new Date();
                long sevenDays = 1000 * 60 * 60 * 24 * 7;
                dialog.getDatePicker().setMinDate(now.getTime() - 10000); //Subtract a second because of a crash on older devices/api levels
                dialog.getDatePicker().setMaxDate(now.getTime() + sevenDays);
                dialog.show();
            }
        });
    }

    private void updateStartsAtTextView() {
        if (mListener.getFlight().getStartsAt() == null) {
            mListener.getFlight().setStartsAt(new Date());
        }
        SimpleDateFormat format = new SimpleDateFormat("M/d/yy h:mm a", Locale.US);
        Date date = mListener.getFlight().getStartsAt();
        startsAtTextView.setText(format.format(date));
    }

    private void setupAircraftDialog() {
        AirMap.getAircraft(new AirMapCallback<List<AirMapAircraft>>() {
            @Override
            public void onSuccess(List<AirMapAircraft> response) {
                if (!isFragmentActive()) {
                    return;
                }

                if (response == null) {
                    response = new ArrayList<>();
                }
                response.add(new AirMapAircraft().setAircraftId("add_aircraft").setNickname("+ Add Aircraft").setModel(new AirMapAircraftModel().setName("").setManufacturer(new AirMapAircraftManufacturer().setName(""))));
                aircraft = response;
            }

            @Override
            public void onError(AirMapException e) {
                e.printStackTrace();
            }
        });

        aircraftTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new AlertDialog.Builder(getContext())
                        .setTitle("Select Aircraft")
                        .setAdapter(new AircraftAdapter(getContext(), aircraft), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int position) {
                                if (aircraft.get(position).getAircraftId().equals("add_aircraft")) {
                                    Intent intent = new Intent(getContext(), CreateEditAircraftActivity.class);
                                    startActivityForResult(intent, REQUEST_CREATE_AIRCRAFT);
                                } else {
                                    mListener.getFlight().setAircraft(aircraft.get(position));
                                    aircraftTextView.setText(aircraft.get(position).getNickname());
                                }
                                dialogInterface.dismiss();
                            }
                        })
                        .show();
            }
        });
    }

    private void onSaveButton() {
        AirMap.createFlight(mListener.getFlight(), new AirMapCallback<AirMapFlight>() {
            @Override
            public void onSuccess(AirMapFlight response) {
                if (!isFragmentActive()) {
                    return;
                }

                hideProgressBar();
                mListener.flightDetailsSaveClicked(response);
            }

            @Override
            public void onError(AirMapException e) {
                if (!isFragmentActive()) {
                    return;
                }


                hideProgressBar();
                saveNextButton.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getContext(), "Error creating flight", Toast.LENGTH_SHORT).show();
                    }
                });
                e.printStackTrace();
            }
        });
    }

    private void onNextButton() {
        AirMapFlight flight = mListener.getFlight();
        AirMap.checkCoordinate(flight.getCoordinate(), flight.getBuffer(), null, null, true, flight.getStartsAt(), new AirMapCallback<AirMapStatus>() {
            @Override
            public void onSuccess(AirMapStatus response) {
                if (!isFragmentActive()) {
                    return;
                }

                hideProgressBar();
                latestStatus = response;
                mListener.flightDetailsNextClicked(response);
            }

            @Override
            public void onError(final AirMapException e) {
                if (!isFragmentActive()) {
                    return;
                }

                hideProgressBar();
                saveNextButton.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void updateSaveNextButtonText() {
        AirMapFlight flight = mListener.getFlight();
        AirMap.checkCoordinate(flight.getCoordinate(), flight.getBuffer(), null, null, false, flight.getStartsAt(), new AirMapCallback<AirMapStatus>() {
            @Override
            public void onSuccess(AirMapStatus airMapStatus) {
                if (!isFragmentActive()) {
                    return;
                }

                latestStatus = airMapStatus;
                hideProgressBar();

                drawRadiusPolygon();

                List<AirMapStatusAdvisory> advisories = airMapStatus.getAdvisories();
                boolean requiresNotice = false;
                boolean requiresPermit = false;
                for (AirMapStatusAdvisory advisory : advisories) {
                    if (advisory.getAvailablePermits() != null && !advisory.getAvailablePermits().isEmpty()) {
                        requiresPermit = true;
                    } else if (advisory.getRequirements() != null && advisory.getRequirements().getNotice() != null) {
                        requiresNotice = true;
                    }
                }

                updateButtonText(requiresPermit || requiresNotice ? R.string.airmap_next : R.string.airmap_save);

                // draw polygons for advisories with permits (green if user has permit, yellow otherwise)
                if (map != null && (airMapStatus.getAdvisories() != null && !airMapStatus.getAdvisories().isEmpty()) || airMapStatus.getAdvisoryColor() == AirMapStatus.StatusColor.Red)  {
                    final Map<String, AirMapStatusAdvisory> advisoryMap = new HashMap<>();

                    for (AirMapStatusAdvisory advisory : airMapStatus.getAdvisories()) {
                        if (advisory.getAvailablePermits() != null && !advisory.getAvailablePermits().isEmpty()) {
                            advisoryMap.put(advisory.getId(), advisory);
                        } else if (advisory.getColor() == AirMapStatus.StatusColor.Red) {
                            advisoryMap.put(advisory.getId(), advisory);
                        }
                    }

                    if (!permitAdvisories.keySet().containsAll(advisoryMap.keySet())) {
                        updatePermitPolygons(advisoryMap);
                    } else {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                List<String> keySet = new ArrayList<>(permitAdvisories.keySet());
                                for (String key : keySet) {
                                    if (!advisoryMap.containsKey(key)) {
                                        permitAdvisories.remove(key);
                                        Polygon polygon = polygonMap.remove(key);
                                        map.removePolygon(polygon);
                                    }
                                }
                            }
                        });
                    }
                }

                // tell the user if conflicting permits
                if (requiresPermit && (latestStatus.getApplicablePermits() == null || latestStatus.getApplicablePermits().isEmpty())) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getActivity(), "Flight area cannot overlap with conflicting permit requirements", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }

            @Override
            public void onError(AirMapException e) {
                if (!isFragmentActive()) {
                    return;
                }

                updateButtonText(R.string.airmap_save);
            }
        });
    }

    private void updateButtonText(@StringRes final int id) {
        saveNextButton.post(new Runnable() {
            @Override
            public void run() {
                saveNextButton.setText(id);
            }
        });
    }

    private void hideProgressBar() {
        progressBarContainer.post(new Runnable() {
            @Override
            public void run() {
                progressBarContainer.setVisibility(View.GONE);
            }
        });
    }


    private void updatePermitPolygons(final Map<String, AirMapStatusAdvisory> advisoryMap) {
        AirspaceService.getAirspace(new ArrayList<>(advisoryMap.keySet()), new AirMapCallback<List<AirMapAirspace>>() {
            @Override
            public void onSuccess(final List<AirMapAirspace> airspacesResponse) {
                // use pilot permits to show green, yellow or red
                if (AirMap.hasValidAuthenticatedUser()) {
                    AirMap.getAuthenticatedPilotPermits(new AirMapCallback<List<AirMapPilotPermit>>() {
                        @Override
                        public void onSuccess(List<AirMapPilotPermit> pilotPermitsResponse) {
                            if (!isFragmentActive()) {
                                return;
                            }

                            final Map<String,AirMapPilotPermit> pilotPermitIds = new HashMap<>();
                            if (pilotPermitsResponse != null) {
                                for (AirMapPilotPermit pilotPermit : pilotPermitsResponse) {
                                    pilotPermitIds.put(pilotPermit.getShortDetails().getPermitId(), pilotPermit);
                                }
                            }

                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    drawPolygons(airspacesResponse, advisoryMap, pilotPermitIds);
                                }
                            });
                        }

                        @Override
                        public void onError(AirMapException e) {
                            Log.e(TAG, "getPilotPermits failed", e);
                        }
                    });
                } else {
                    if (!isFragmentActive()) {
                        return;
                    }

                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            drawPolygons(airspacesResponse, advisoryMap, null);
                        }
                    });
                }
            }

            @Override
            public void onError(AirMapException e) {
                Log.e(TAG, "getAirspaces failed", e);
            }
        });
    }

    private void drawPolygons(final List<AirMapAirspace> airspaces, final Map<String,AirMapStatusAdvisory> advisoryMap, Map<String, AirMapPilotPermit> pilotPermitIds) {
        if (map == null) {
            return;
        }

        Map<String,AirMapStatusAdvisory> updatedMap = new HashMap<>();
        Map<String,Polygon> polygonsToRemove = new HashMap<>(polygonMap);

        for (AirMapAirspace airspace : airspaces) {
            if (!advisoryMap.containsKey(airspace.getAirspaceId())) {
                continue;
            }

            AirMapStatusAdvisory advisory = advisoryMap.get(airspace.getAirspaceId());
            updatedMap.put(advisory.getId(), advisory);

            AirMapStatus.StatusColor statusColor = AirMapStatus.StatusColor.Yellow;
            if (pilotPermitIds != null) {
                for (AirMapAvailablePermit availablePermit : advisory.getAvailablePermits()) {
                    AirMapPilotPermit pilotPermit = pilotPermitIds.get(availablePermit.getId());
                    if (pilotPermit != null && (pilotPermit.getExpiresAt() == null || pilotPermit.getExpiresAt().after(new Date()))) {
                        statusColor = AirMapStatus.StatusColor.Green;
                        break;
                    }
                }
            }

            if (advisory.getColor() == AirMapStatus.StatusColor.Red) {
                statusColor = AirMapStatus.StatusColor.Red;
            }


            if (!polygonMap.containsKey(airspace.getAirspaceId())) {
                Polygon polygon = drawPermitPolygon(airspace, statusColor);
                if (polygon != null) {
                    polygonMap.put(airspace.getAirspaceId(), polygon);
                }
            }
            polygonsToRemove.remove(airspace.getAirspaceId());
            advisoryMap.remove(airspace.getAirspaceId());
        }

        // remove polygons no longer valid
        for (String key : polygonsToRemove.keySet()) {
            Polygon polygon = polygonsToRemove.get(key);
            map.removePolygon(polygon);
            polygonMap.remove(key);
        }

        // redraw flight radius so its highest in z-index
        drawRadiusPolygon();

        permitAdvisories = updatedMap;
    }

    private Polygon drawPermitPolygon(AirMapAirspace airspace, AirMapStatus.StatusColor statusColor) {
        AirMapGeometry geometry = airspace.getGeometry();
        if (geometry instanceof AirMapPolygon) {
            PolygonOptions polygonOptions = Utils.getMapboxPolygon((AirMapPolygon) geometry);

            int color;
            switch (statusColor) {
                case Red: {
                    color = getResources().getColor(R.color.airmap_red);
                    polygonOptions.alpha(0.75f);
                    break;
                }
                case Yellow: {
                    color = getResources().getColor(R.color.airmap_yellow);
                    polygonOptions.alpha(0.6f);
                    break;
                }
                default:
                case Green: {
                    color = getResources().getColor(R.color.airmap_green);
                    polygonOptions.alpha(0.6f);
                    break;
                }
            }
            polygonOptions.fillColor(color);
            return map.addPolygon(polygonOptions);
        }

        //TODO: handle airspaces that are advisories that aren't polygons?
        return null;
    }

    private void drawRadiusPolygon() {
        if (map == null) {
            return;
        }

        //FIXME: this is a hack to change the z-index of the radius polygon
        //FIXME: if its not delayed on UI thread it doesn't work :(
        new Handler(getActivity().getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (radiusPolygon != null) {
                    map.removePolygon(radiusPolygon.getPolygon());
                }

                int color = getStatusCircleColor(latestStatus, getContext());
                radiusPolygon = getCirclePolygon(getBufferPresets()[Math.min(radiusSeekBar.getProgress(), getBufferPresets().length - 1)].value.doubleValue(), mListener.getFlight().getCoordinate(), color);
                map.addPolygon(radiusPolygon);
            }
        }, 10);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CREATE_AIRCRAFT) {
            if (resultCode == Activity.RESULT_OK) {
                AirMapAircraft newAircraft = (AirMapAircraft) data.getSerializableExtra(CreateEditAircraftActivity.AIRCRAFT);
                aircraft.add(newAircraft);
                mListener.getFlight().setAircraft(newAircraft);
                aircraftTextView.setText(newAircraft.getNickname());
            }
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    protected boolean isFragmentActive() {
        return getActivity() != null && !getActivity().isFinishing() && isResumed();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    public abstract class SeekBarChangeListener implements SeekBar.OnSeekBarChangeListener {

        @Override
        public abstract void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser);

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            updateSaveNextButtonText();
            mListener.flightChanged();
        }
    }

    public abstract class AnimationListener implements Animator.AnimatorListener {
        @Override
        public void onAnimationStart(Animator animation) {

        }

        @Override
        public abstract void onAnimationEnd(Animator animation);

        @Override
        public void onAnimationCancel(Animator animation) {

        }

        @Override
        public void onAnimationRepeat(Animator animation) {

        }
    }

    public interface OnFragmentInteractionListener {
        AirMapFlight getFlight();

        void flightDetailsSaveClicked(AirMapFlight response);

        void flightDetailsNextClicked(AirMapStatus flightStatus);

        void flightChanged();

        void setPilot(AirMapPilot response);
    }
}