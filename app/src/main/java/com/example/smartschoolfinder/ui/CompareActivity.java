package com.example.smartschoolfinder.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.content.ContextCompat;

import com.example.smartschoolfinder.R;
import com.example.smartschoolfinder.data.CompareRepository;
import com.example.smartschoolfinder.data.TransportRepository;
import com.example.smartschoolfinder.model.School;
import com.example.smartschoolfinder.model.TransportInfo;
import com.example.smartschoolfinder.network.ApiCallback;
import com.example.smartschoolfinder.network.SchoolApiService;
import com.example.smartschoolfinder.utils.LocaleUtils;
import com.example.smartschoolfinder.utils.TransportUiFormatter;
import com.example.smartschoolfinder.widget.CompareSideBar;
import com.example.smartschoolfinder.widget.SideBar;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CompareActivity extends AppCompatActivity {
    private TextView tvSchoolASelect;
    private TextView tvSchoolBSelect;
    private TableLayout tableCompare;
    private TransportRepository transportRepository;

    private List<School> schools = new ArrayList<>();
    private final Map<String, Integer> letterPositionMap = new LinkedHashMap<>();
    private int selectedA = 0;
    private int selectedB = 0;
    private final Collator zhCollator = Collator.getInstance(Locale.CHINA);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compare);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        tvSchoolASelect = findViewById(R.id.tvSchoolASelect);
        tvSchoolBSelect = findViewById(R.id.tvSchoolBSelect);
        tableCompare = findViewById(R.id.tableCompare);
        Button btnDoCompare = findViewById(R.id.btnDoCompare);

        transportRepository = new TransportRepository();
        zhCollator.setStrength(Collator.PRIMARY);
        btnDoCompare.setOnClickListener(v -> showComparison());
        applyPressFeedback(btnDoCompare, tvSchoolASelect, tvSchoolBSelect);

        tvSchoolASelect.setOnClickListener(v -> showSchoolPickerDialog(true));
        tvSchoolBSelect.setOnClickListener(v -> showSchoolPickerDialog(false));
        loadSchools();
    }

    private void loadSchools() {
        new SchoolApiService().getSchools(this, new ApiCallback<List<School>>() {
            @Override
            public void onSuccess(List<School> data) {
                schools = data == null ? new ArrayList<>() : new ArrayList<>(data);
                Collections.sort(schools, schoolComparator());
                rebuildLetterPositionMap();
                setDefaultCompareSelection();
                refreshSelectedSchoolLabels();
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
                    selectedA = i;
                    break;
                }
            }
        }
        if (idB != null) {
            for (int i = 0; i < schools.size(); i++) {
                if (schools.get(i).getId().equals(idB)) {
                    selectedB = i;
                    break;
                }
            }
        }
    }

    private void showSchoolPickerDialog(boolean forA) {
        if (schools.isEmpty()) return;
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_school_picker, null, false);
        RecyclerView recycler = content.findViewById(R.id.recyclerSchoolPicker);
        CompareSideBar sideBar = content.findViewById(R.id.sideBarSchoolPicker);
        TextView tvHint = content.findViewById(R.id.tvLetterHintSchoolPicker);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        SchoolPickerAdapter adapter = new SchoolPickerAdapter(schools, school -> {
            int index = schools.indexOf(school);
            if (index < 0) return;
            if (forA) selectedA = index; else selectedB = index;
            refreshSelectedSchoolLabels();
        });
        recycler.setAdapter(adapter);
        recycler.scrollToPosition(forA ? selectedA : selectedB);

        sideBar.setOnLetterChangedListener(new SideBar.OnLetterChangedListener() {
            @Override
            public void onLetterChanged(String letter) {
                tvHint.setText(letter);
                tvHint.setVisibility(View.VISIBLE);
                Integer pos = findNearestLetterPosition(letter);
                if (pos != null) {
                    ((LinearLayoutManager) recycler.getLayoutManager()).scrollToPositionWithOffset(pos, 0);
                }
            }

            @Override
            public void onTouchReleased() {
                tvHint.setVisibility(View.GONE);
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(forA ? getString(R.string.compare_select_school_a) : getString(R.string.compare_select_school_b))
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .create();
        adapter.setOnItemClick(dialog::dismiss);
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(ContextCompat.getColor(this, R.color.compare_dialog_action_text));
    }

    private Integer findNearestLetterPosition(String letter) {
        if (letter == null) return null;
        Integer exact = letterPositionMap.get(letter);
        if (exact != null) return exact;
        String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#";
        int start = letters.indexOf(letter);
        if (start < 0) return null;
        for (int i = start + 1; i < letters.length(); i++) {
            Integer pos = letterPositionMap.get(String.valueOf(letters.charAt(i)));
            if (pos != null) return pos;
        }
        return null;
    }

    private void refreshSelectedSchoolLabels() {
        if (schools.isEmpty()) return;
        selectedA = clampIndex(selectedA);
        selectedB = clampIndex(selectedB);
        tvSchoolASelect.setText(displayName(schools.get(selectedA)));
        tvSchoolBSelect.setText(displayName(schools.get(selectedB)));
    }

    private int clampIndex(int index) {
        if (index < 0) return 0;
        if (index >= schools.size()) return schools.size() - 1;
        return index;
    }

    private void rebuildLetterPositionMap() {
        letterPositionMap.clear();
        for (int i = 0; i < schools.size(); i++) {
            String letter = getSectionLetter(displayName(schools.get(i)));
            if (!letterPositionMap.containsKey(letter)) {
                letterPositionMap.put(letter, i);
            }
        }
    }

    private Comparator<School> schoolComparator() {
        return (a, b) -> {
            String nameA = displayName(a);
            String nameB = displayName(b);
            int typeA = nameType(nameA);
            int typeB = nameType(nameB);
            if (typeA != typeB) return Integer.compare(typeA, typeB);
            if (typeA == 0) return nameA.compareToIgnoreCase(nameB);
            if (typeA == 1) return zhCollator.compare(nameA, nameB);
            return nameA.compareToIgnoreCase(nameB);
        };
    }

    private String displayName(School school) {
        if (school == null || school.getName() == null) return "";
        return school.getName().trim();
    }

    private int nameType(String name) {
        if (name == null || name.trim().isEmpty()) return 2;
        char c = name.trim().charAt(0);
        if (isEnglishLetter(c)) return 0;
        if (isChinese(c)) return 1;
        return 2;
    }

    private boolean isEnglishLetter(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private boolean isChinese(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }

    private String getSectionLetter(String name) {
        if (name == null) return "#";
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return "#";
        char first = trimmed.charAt(0);
        if (isEnglishLetter(first)) return String.valueOf(Character.toUpperCase(first));
        if (isChinese(first)) return chineseInitial(first);
        return "#";
    }

    private String chineseInitial(char ch) {
        String[] letters = {"A","B","C","D","E","F","G","H","J","K","L","M","N","O","P","Q","R","S","T","W","X","Y","Z"};
        String[] boundaries = {"阿","芭","擦","搭","蛾","发","噶","哈","机","喀","垃","妈","拿","哦","啪","期","然","撒","塌","挖","昔","压","匝"};
        String s = String.valueOf(ch);
        for (int i = boundaries.length - 1; i >= 0; i--) {
            if (zhCollator.compare(s, boundaries[i]) >= 0) return letters[i];
        }
        return "#";
    }

    private void showComparison() {
        if (schools.isEmpty()) return;
        selectedA = clampIndex(selectedA);
        selectedB = clampIndex(selectedB);
        final School a = schools.get(selectedA);
        final School b = schools.get(selectedB);

        tableCompare.removeAllViews();
        addHeaderRow(displayName(a), displayName(b));
        addRow(getString(R.string.field_district), safeValue(a.getDistrict()), safeValue(b.getDistrict()));
        addRow(getString(R.string.field_type), safeValue(a.getType()), safeValue(b.getType()));
        addRow(getString(R.string.field_phone), safeValue(a.getPhone()), safeValue(b.getPhone()));
        addRow(getString(R.string.field_tuition), safeValue(a.getTuition()), safeValue(b.getTuition()));

        final TransportInfo[] transportResults = new TransportInfo[2];
        final int[] pending = {2};
        final boolean preferZh = LocaleUtils.prefersChineseSchoolData(this);

        Runnable appendTransportRows = () -> {
            pending[0]--;
            if (pending[0] != 0) return;
            addRow(getString(R.string.field_mtr),
                    transportLineValue(transportResults[0] == null ? null : transportResults[0].getMtrStation(),
                            transportResults[0] == null ? null : transportResults[0].getMtrDistance()),
                    transportLineValue(transportResults[1] == null ? null : transportResults[1].getMtrStation(),
                            transportResults[1] == null ? null : transportResults[1].getMtrDistance()));
            addRow(getString(R.string.field_bus),
                    transportLineValue(transportResults[0] == null ? null : transportResults[0].getBusStation(),
                            transportResults[0] == null ? null : transportResults[0].getBusDistance()),
                    transportLineValue(transportResults[1] == null ? null : transportResults[1].getBusStation(),
                            transportResults[1] == null ? null : transportResults[1].getBusDistance()));
            addRow(getString(R.string.field_minibus),
                    transportLineValue(transportResults[0] == null ? null : transportResults[0].getMinibusStation(),
                            transportResults[0] == null ? null : transportResults[0].getMinibusDistance()),
                    transportLineValue(transportResults[1] == null ? null : transportResults[1].getMinibusStation(),
                            transportResults[1] == null ? null : transportResults[1].getMinibusDistance()));
            addRow(getString(R.string.field_convenience),
                    safeValue(transportResults[0] == null ? null : transportResults[0].getConvenienceScore()),
                    safeValue(transportResults[1] == null ? null : transportResults[1].getConvenienceScore()));
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

    private void addHeaderRow(String schoolA, String schoolB) {
        TableRow row = new TableRow(this);
        row.setLayoutParams(new TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT));

        TextView tvField = buildCell(getString(R.string.compare_field), 0.9f, true,
                ContextCompat.getColor(this, R.color.compare_field_text));
        TextView tvA = buildCell(safeValue(schoolA), 1f, true,
                ContextCompat.getColor(this, R.color.compare_value_text_emphasis));
        TextView tvB = buildCell(safeValue(schoolB), 1f, true,
                ContextCompat.getColor(this, R.color.compare_value_text_emphasis));

        row.addView(tvField);
        row.addView(tvA);
        row.addView(tvB);
        tableCompare.addView(row);
        addDivider();
    }

    private void addRow(String field, String valueA, String valueB) {
        valueA = safeValue(valueA);
        valueB = safeValue(valueB);
        TableRow row = new TableRow(this);
        row.setLayoutParams(new TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT));

        boolean diff = !valueA.equals(valueB);
        if (diff) {
            row.setBackgroundColor(ContextCompat.getColor(this, R.color.compare_diff_row_bg));
        }

        TextView tvField = buildCell(field, 0.9f, false,
                ContextCompat.getColor(this, R.color.compare_field_text));
        TextView tvA = buildCell(valueA, 1f, diff,
                ContextCompat.getColor(this, diff ? R.color.compare_value_text_emphasis : R.color.compare_value_text));
        TextView tvB = buildCell(valueB, 1f, diff,
                ContextCompat.getColor(this, diff ? R.color.compare_value_text_emphasis : R.color.compare_value_text));
        if (isNoDataText(valueA)) {
            tvA.setTextColor(ContextCompat.getColor(this, R.color.compare_no_data_text));
        }
        if (isNoDataText(valueB)) {
            tvB.setTextColor(ContextCompat.getColor(this, R.color.compare_no_data_text));
        }

        row.addView(tvField);
        row.addView(tvA);
        row.addView(tvB);
        tableCompare.addView(row);
        addDivider();
    }

    private TextView buildCell(String text, float weight, boolean bold, int color) {
        TextView tv = new TextView(this);
        TableRow.LayoutParams lp = new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, weight);
        tv.setLayoutParams(lp);
        tv.setPadding(12, 10, 12, 10);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(13f);
        if (bold) {
            tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return tv;
    }

    private void addDivider() {
        View line = new View(this);
        TableLayout.LayoutParams lp = new TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT, 1);
        line.setLayoutParams(lp);
        line.setBackgroundColor(ContextCompat.getColor(this, R.color.compare_divider));
        tableCompare.addView(line);
    }

    private String safeValue(String value) {
        if (value == null) return getString(R.string.no_data);
        String v = value.trim();
        if (v.isEmpty()) return getString(R.string.no_data);
        String lower = v.toLowerCase(Locale.ROOT);
        if ("n/a".equals(lower) || "na".equals(lower) || "-".equals(lower) || "無".equals(v) || "无".equals(v)) {
            return getString(R.string.no_data);
        }
        return v;
    }

    private boolean isNoDataText(String value) {
        return value != null && value.equals(getString(R.string.no_data));
    }

    private String transportLineValue(String stationRaw, String distanceRaw) {
        String station = safeValue(TransportUiFormatter.formatPlace(this, stationRaw));
        String distance = safeValue(TransportUiFormatter.formatDistance(this, distanceRaw));
        if (getString(R.string.no_data).equals(station) && getString(R.string.no_data).equals(distance)) {
            return getString(R.string.no_data);
        }
        if (getString(R.string.no_data).equals(distance)) {
            return station;
        }
        if (getString(R.string.no_data).equals(station)) {
            return distance;
        }
        return station + " (" + distance + ")";
    }

    private void applyPressFeedback(View... views) {
        for (View v : views) {
            v.setOnTouchListener((view, event) -> {
                if (!view.isEnabled()) return false;
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

    private static final class SchoolPickerAdapter extends RecyclerView.Adapter<SchoolPickerAdapter.Holder> {
        interface OnSchoolPicked { void onPick(School school); }

        private final List<School> items;
        private final OnSchoolPicked onSchoolPicked;
        private Runnable onItemClick;

        SchoolPickerAdapter(List<School> items, OnSchoolPicked onSchoolPicked) {
            this.items = items;
            this.onSchoolPicked = onSchoolPicked;
        }

        void setOnItemClick(Runnable onItemClick) { this.onItemClick = onItemClick; }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setPadding(24, 20, 24, 20);
            tv.setTextSize(14f);
            tv.setTextColor(ContextCompat.getColor(parent.getContext(), R.color.compare_picker_item_text));
            return new Holder(tv);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            School school = items.get(position);
            holder.textView.setText(school == null || school.getName() == null ? "" : school.getName());
            holder.textView.setOnClickListener(v -> {
                onSchoolPicked.onPick(school);
                if (onItemClick != null) onItemClick.run();
            });
        }

        @Override
        public int getItemCount() { return items == null ? 0 : items.size(); }

        static final class Holder extends RecyclerView.ViewHolder {
            final TextView textView;
            Holder(View itemView) {
                super(itemView);
                textView = (TextView) itemView;
            }
        }
    }
}
