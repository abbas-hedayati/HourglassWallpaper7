package com.example.hourglass;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Asks for the number of seconds, saves it, then opens the wallpaper picker. */
public class MainActivity extends Activity {

    private EditText input;
    private EditText fallenInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#1A1040"));
        int pad = dp(24);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("شمارنده‌ی معکوس ساعت شنی");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("تعداد ثانیه‌ها را وارد کنید (مثلاً 1258642380)");
        hint.setTextColor(0xB3E0D8FF);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(12), 0, dp(16));
        root.addView(hint);

        input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0x88FFFFFF);
        input.setHint("ثانیه");
        input.setGravity(Gravity.CENTER);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        SharedPreferences prefs = getSharedPreferences("hourglass", MODE_PRIVATE);
        long saved = prefs.getLong("total", HourglassWallpaperService.DEFAULT_SECONDS);
        input.setText(String.valueOf(saved));
        root.addView(input);

        TextView hint2 = new TextView(this);
        hint2.setText("تعداد شن‌های قهوه‌ای افتاده در پایین");
        hint2.setTextColor(0xB3E0D8FF);
        hint2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        hint2.setGravity(Gravity.CENTER);
        hint2.setPadding(0, dp(16), 0, dp(8));
        root.addView(hint2);

        fallenInput = new EditText(this);
        fallenInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        fallenInput.setTextColor(Color.WHITE);
        fallenInput.setHintTextColor(0x88FFFFFF);
        fallenInput.setHint("شن قهوه‌ای");
        fallenInput.setGravity(Gravity.CENTER);
        fallenInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        fallenInput.setText(String.valueOf(prefs.getLong("fallen", 0)));
        root.addView(fallenInput);

        Button save = new Button(this);
        save.setText("ذخیره و تنظیم والپیپر");
        save.setOnClickListener(v -> onSave());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(20);
        root.addView(save, lp);

        setContentView(root);
    }

    private void onSave() {
        String digits = input.getText().toString().replaceAll("[^0-9]", "");
        long seconds;
        try {
            seconds = Long.parseLong(digits);
        } catch (NumberFormatException e) {
            seconds = 0;
        }
        if (seconds <= 0) {
            Toast.makeText(this, "یک عدد معتبر (بزرگ‌تر از صفر) وارد کنید", Toast.LENGTH_SHORT).show();
            return;
        }

        long fallen;
        try {
            fallen = Long.parseLong(fallenInput.getText().toString().replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            fallen = 0;
        }

        // Start a new countdown from now with the given number of seconds.
        getSharedPreferences("hourglass", MODE_PRIVATE).edit()
                .putLong("total", seconds)
                .putLong("fallen", fallen)
                .putLong("end", (System.currentTimeMillis() / 1000L) * 1000L + seconds * 1000L)
                .apply();

        Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                new ComponentName(this, HourglassWallpaperService.class));
        try {
            startActivity(intent);
        } catch (Exception e) {
            startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
        }
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }
}
