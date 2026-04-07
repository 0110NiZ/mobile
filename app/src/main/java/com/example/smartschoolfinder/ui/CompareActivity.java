package com.example.smartschoolfinder.ui;

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartschoolfinder.R;
import com.example.smartschoolfinder.data.CompareRepository;
import com.example.smartschoolfinder.data.TransportRepository;
import com.example.smartschoolfinder.model.School;
import com.example.smartschoolfinder.model.TransportInfo;
import com.example.smartschoolfinder.network.ApiCallback;
import com.example.smartschoolfinder.network.SchoolApiService;
import com.example.smartschoolfinder.utils.LocaleUtils;
import com.example.smartschoolfinder.utils.TransportUiFormatter;

import java.util.ArrayList;
import java.util.List;

public class CompareActivity extends AppCompatActivity {

    private Spinner spinnerA;
    private Spinner spinnerB;
    private TableLayout tableCompare;
    private TransportRepository transportRepository;

    private List<School> schools = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compare);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        spinnerA = findViewById(R.id.spinnerSchoolA);
        spinnerB = findViewById(R.id.spinnerSchoolB);
        tableCompare = findViewById(R.id.tableCompare);
        Button btnDoCompare = findViewById(R.id.btnDoCompare);

        transportRepository = new TransportRepository();

        btnDoCompare.setOnClickListener(v -> showComparison());
        applyPressFeedback(btnDoCompare);
        AdapterView.OnItemSelectedListener feedbackListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                pulseView(parent);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
        spinnerA.setOnItemSelectedListener(feedbackListener);
        spinnerB.setOnItemSelectedListener(feedbackListener);

        loadSchools();
    }

    private void loadSchools() {
        new SchoolApiService().getSchools(this, new ApiCallback<List<School>>() {
            @Override
            public void onSuccess(List<School> data) {
                schools = data;
                List<String> names = new ArrayList<>();
                for (School school : schools) {
                    names.add(school.getName());
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(CompareActivity.this,
                        android.R.layout.simple_spinner_item, names);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerA.setAdapter(adapter);
                spinnerB.setAdapter(adapter);

                setDefaultCompareSelection();
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    private void setDefaultCompareSelection() {
        String idA = CompareRepository.getSchoolAId();
        String idB = CompareRepository.getSchoolBId();

        if (idA != null) {
            for (int i = 0; i < schools.size(); i++) {
                if (schools.get(i).getId().equals(idA)) {
                    spinnerA.setSelection(i);
                    break;
                }
            }
        }

        if (idB != null) {
            for (int i = 0; i < schools.size(); i++) {
                if (schools.get(i).getId().equals(idB)) {
                    spinnerB.setSelection(i);
                    break;
                }
            }
        }
    }

    private void showComparison() {
        if (schools.isEmpty()) {
            return;
        }

        final School a = schools.get(spinnerA.getSelectedItemPosition());
        final School b = schools.get(spinnerB.getSelectedItemPosition());

        tableCompare.removeAllViews();
        addRow(getString(R.string.compare_field), a.getName(), b.getName());
        addRow(getString(R.string.compare_district), a.getDistrict(), b.getDistrict());
        addRow(getString(R.string.compare_type), a.getType(), b.getType());
        addRow(getString(R.string.compare_phone), a.getPhone(), b.getPhone());
        addRow(getString(R.string.compare_tuition), a.getTuition(), b.getTuition());

        final TransportInfo[] transportResults = new TransportInfo[2];
        final int[] pending = {2};
        final boolean preferZh = LocaleUtils.prefersChineseSchoolData(this);

        Runnable appendTransportRows = () -> {
            pending[0]--;
            if (pending[0] != 0) {
                return;
            }
            addRow(getString(R.string.compare_row_mtr),
                    TransportUiFormatter.lineMtr(CompareActivity.this, transportResults[0]),
                    TransportUiFormatter.lineMtr(CompareActivity.this, transportResults[1]));
            addRow(getString(R.string.compare_row_bus),
                    TransportUiFormatter.lineBus(CompareActivity.this, transportResults[0]),
                    TransportUiFormatter.lineBus(CompareActivity.this, transportResults[1]));
            addRow(getString(R.string.compare_row_minibus),
                    TransportUiFormatter.lineMinibus(CompareActivity.this, transportResults[0]),
                    TransportUiFormatter.lineMinibus(CompareActivity.this, transportResults[1]));
            addRow(getString(R.string.compare_row_convenience),
                    TransportUiFormatter.lineConvenience(CompareActivity.this, transportResults[0]),
                    TransportUiFormatter.lineConvenience(CompareActivity.this, transportResults[1]));
        };

        requestTransportForSchool(a.getId(), preferZh, transportResults, 0, appendTransportRows);
        requestTransportForSchool(b.getId(), preferZh, transportResults, 1, appendTransportRows);
    }

    private void requestTransportForSchool(String schoolId, boolean preferZh,
                                           TransportInfo[] out, int index, Runnable onDone) {
        if (schoolId == null || schoolId.trim().isEmpty()) {
            out[index] = null;
            onDone.run();
            return;
        }
        transportRepository.getSchoolTransport(schoolId, preferZh, new ApiCallback<TransportInfo>() {
            @Override
            public void onSuccess(TransportInfo data) {
                out[index] = data;
                onDone.run();
            }

            @Override
            public void onError(String message) {
                out[index] = null;
                onDone.run();
            }
        });
    }

    private void addRow(String field, String valueA, String valueB) {
        TableRow row = new TableRow(this);

        TextView tvField = new TextView(this);
        TextView tvA = new TextView(this);
        TextView tvB = new TextView(this);

        tvField.setPadding(12, 12, 12, 12);
        tvA.setPadding(12, 12, 12, 12);
        tvB.setPadding(12, 12, 12, 12);

        tvField.setText(field);
        tvA.setText(valueA);
        tvB.setText(valueB);

        row.addView(tvField);
        row.addView(tvA);
        row.addView(tvB);
        tableCompare.addView(row);
    }

    private void applyPressFeedback(View... views) {
        for (View v : views) {
            v.setOnTouchListener((view, event) -> {
                if (!view.isEnabled()) {
                    return false;
                }
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    view.animate().scaleX(0.98f).scaleY(0.98f).alpha(0.95f).setDuration(100).start();
                } else if (event.getAction() == MotionEvent.ACTION_UP
                        || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(140).start();
                }
                return false;
            });
        }
    }

    private void pulseView(View view) {
        view.animate().scaleX(0.98f).scaleY(0.98f).setDuration(80).withEndAction(
                () -> view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        ).start();
    }
}
