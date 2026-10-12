package market.dpad.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int BG = 0xff111315, PANEL = 0xff191c1f, PANEL_HI = 0xff24292c;
    private static final int RULE = 0xff34393d, FG = 0xffe9ece9, MUTED = 0xff9aa1a2;
    private static final int LIME = 0xffd8ed63, ORANGE = 0xfff47a45, BLUE = 0xff91cfff;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<AppEntry> catalog = new ArrayList<>();
    private LinearLayout root;
    private String activeCategory = "All";
    private String query = "";
    private AppEntry selected;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        loadCatalog();
        showHome();
    }

    private void loadCatalog() {
        try (InputStream in = getAssets().open("catalog.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            JSONArray array = new JSONObject(out.toString("UTF-8")).getJSONArray("apps");
            for (int i = 0; i < array.length(); i++) catalog.add(AppEntry.from(array.getJSONObject(i)));
            JSONArray personal = new JSONArray(getPreferences(MODE_PRIVATE).getString("user_sources", "[]"));
            for (int i = 0; i < personal.length(); i++) catalog.add(AppEntry.from(personal.getJSONObject(i)));
        } catch (Exception e) { Toast.makeText(this, "Could not read the app catalog", Toast.LENGTH_LONG).show(); }
    }

    private void base(String title) {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        TextView brand = text("+  DPAD MARKET", 17, LIME, true);
        brand.setPadding(dp(22), dp(17), dp(18), dp(16)); brand.setBackgroundColor(BG);
        brand.setFocusable(false);
        root.addView(brand, new LinearLayout.LayoutParams(-1, -2));
        TextView bar = text(title.toUpperCase(Locale.ROOT) + "     ·     " + catalog.size() + " APPS", 11, MUTED, true);
        bar.setPadding(dp(22), dp(11), dp(18), dp(11)); bar.setBackgroundColor(PANEL);
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(32));
        scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root); currentContent = content;
    }
    private LinearLayout currentContent;

    private void showHome() {
        base("EXPLORE");
        LinearLayout titleRow = new LinearLayout(this); titleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleCopy = new LinearLayout(this); titleCopy.setOrientation(LinearLayout.VERTICAL);
        titleCopy.addView(text("Apps", 27, FG, true));
        titleCopy.addView(text("Curated for button-first Android", 12, MUTED, false));
        titleRow.addView(titleCopy, new LinearLayout.LayoutParams(0, -2, 1));
        Button addSource = button("+  ADD SOURCE", BG, LIME); addSource.setTextSize(10);
        addSource.setMinHeight(dp(40)); titleRow.addView(addSource); addSource.setOnClickListener(v -> promptAddSource());
        currentContent.addView(titleRow, bottom(dp(18)));

        EditText search = new EditText(this); search.setSingleLine(true); search.setTextSize(14); search.setTextColor(FG);
        search.setHintTextColor(MUTED); search.setHint("⌕   Search apps and developers"); search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setPadding(dp(13), dp(4), dp(13), dp(4)); search.setBackgroundColor(PANEL);
        search.setText(query); currentContent.addView(search, bottom(dp(12)));
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int before, int count) { query = s.toString(); refreshList(); }
            public void afterTextChanged(android.text.Editable e) {}
        });
        HorizontalScrollView filtersScroll = new HorizontalScrollView(this); filtersScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filters = new LinearLayout(this); filters.setOrientation(LinearLayout.HORIZONTAL);
        for (String category : new String[]{"All", "Media", "Audio", "Tools", "Maps", "Community"}) {
            Button chip = button(category, category.equals(activeCategory) ? BG : FG, category.equals(activeCategory) ? LIME : PANEL_HI);
            chip.setTextSize(12); chip.setMinHeight(0); chip.setMinimumHeight(0);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, dp(42)); cp.setMargins(0, 0, dp(7), 0);
            filters.addView(chip, cp); chip.setOnClickListener(v -> { activeCategory = category; showHome(); });
        }
        filtersScroll.addView(filters); currentContent.addView(filtersScroll, bottom(dp(20)));
        resultsLabel = text("CURATOR PICKS  ·  " + visibleCount() + " APPS", 10, MUTED, true);
        currentContent.addView(resultsLabel, bottom(dp(9)));
        listHost = new LinearLayout(this); listHost.setOrientation(LinearLayout.VERTICAL); currentContent.addView(listHost);
        refreshList();
    }

    private int visibleCount() {
        int count = 0;
        for (AppEntry app : catalog) {
            if (!activeCategory.equals("All") && !app.category.equalsIgnoreCase(activeCategory)) continue;
            String haystack = (app.name + " " + app.developer + " " + app.summary + " " + app.category).toLowerCase(Locale.ROOT);
            if (haystack.contains(query.toLowerCase(Locale.ROOT))) count++;
        }
        return count;
    }
    private LinearLayout listHost;
    private TextView resultsLabel;

    private void refreshList() {
        if (listHost == null) return;
        if (resultsLabel != null) resultsLabel.setText((activeCategory.equals("All") ? "CURATOR PICKS" : activeCategory.toUpperCase(Locale.ROOT)) + "  ·  " + visibleCount() + " APPS");
        listHost.removeAllViews();
        int count = 0;
        for (AppEntry app : catalog) {
            if (!activeCategory.equals("All") && !app.category.equalsIgnoreCase(activeCategory)) continue;
            String haystack = (app.name + " " + app.developer + " " + app.summary + " " + app.category).toLowerCase(Locale.ROOT);
            if (!haystack.contains(query.toLowerCase(Locale.ROOT))) continue;
            listHost.addView(appRow(app)); count++;
        }
        if (count == 0) listHost.addView(text("No matching apps.", 14, MUTED, false));
        else { View first = listHost.getChildAt(0); first.post(first::requestFocus); }
    }

    private View appRow(AppEntry app) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL);
        row.setFocusable(true); row.setClickable(true); row.setBackground(selectable()); row.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = text(app.name.substring(0, 1).toUpperCase(Locale.ROOT), 17, LIME, true);
        mark.setGravity(Gravity.CENTER); mark.setBackgroundColor(PANEL_HI);
        top.addView(mark, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout names = new LinearLayout(this); names.setOrientation(LinearLayout.VERTICAL); names.setPadding(dp(12), 0, 0, 0);
        names.addView(text(app.name, 15, FG, true)); names.addView(text(app.developer + "  ·  " + app.category, 10, MUTED, false));
        top.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(text("›", 27, ORANGE, false)); row.addView(top);
        TextView summary = text(app.summary, 12, MUTED, false); summary.setMaxLines(2); summary.setPadding(dp(54), dp(6), 0, dp(3)); row.addView(summary);
        TextView status = text((app.unlisted ? "UNLISTED SOURCE" : "D-PAD  ·  " + app.inputSupport).toUpperCase(Locale.ROOT), 9,
                app.unlisted ? ORANGE : (app.inputSupport.contains("TV") ? LIME : ORANGE), true);
        status.setPadding(dp(54), 0, 0, 0); row.addView(status);
        row.setOnClickListener(v -> showDetail(app)); row.setOnKeyListener((v, key, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP && (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER)) { showDetail(app); return true; }
            return false;
        });
        return row;
    }

    private void showDetail(AppEntry app) {
        selected = app; base("APP DETAILS");
        addBack(currentContent, "←  APPS");
        LinearLayout identity = new LinearLayout(this); identity.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = text(app.name.substring(0, 1).toUpperCase(Locale.ROOT), 25, LIME, true);
        icon.setGravity(Gravity.CENTER); icon.setBackgroundColor(PANEL_HI);
        identity.addView(icon, new LinearLayout.LayoutParams(dp(64), dp(64)));
        LinearLayout names = new LinearLayout(this); names.setOrientation(LinearLayout.VERTICAL); names.setPadding(dp(14), 0, 0, 0);
        names.addView(text(app.name, 24, FG, true));
        names.addView(text(app.developer, 12, MUTED, false));
        identity.addView(names, new LinearLayout.LayoutParams(0, -2, 1)); currentContent.addView(identity, bottom(dp(18)));
        TextView category = text(app.category.toUpperCase(Locale.ROOT) + "  ·  " + app.platform.toUpperCase(Locale.ROOT), 10, ORANGE, true);
        currentContent.addView(category, bottom(dp(9)));
        currentContent.addView(text(app.summary, 15, FG, false), bottom(dp(16)));
        Button install = button("INSTALL LATEST APK   ↓", BG, LIME);
        currentContent.addView(install, bottom(dp(10)));
        install.setOnClickListener(v -> installLatest(app));
        TextView release = text("Fetching latest GitHub release…", 12, MUTED, false);
        release.setPadding(0, dp(5), 0, dp(17)); currentContent.addView(release);
        fetchLatest(app, release);
        sectionLabel(currentContent, "ABOUT THIS APP");
        currentContent.addView(text(app.unlisted ? "Added from your GitHub sources. This listing has not been reviewed by DPAD Market.\n\n" + app.curatorNote : app.curatorNote, 13, MUTED, false), bottom(dp(18)));
        addMeta("D-pad support", app.unlisted ? "Unlisted · not reviewed" : app.inputSupport);
        addMeta("License", app.license);
        addMeta("Source repository", app.repository);
        TextView safety = text("GitHub source is not a security audit. Android confirms the install; a published GitHub SHA-256 is checked before handoff.", 11, MUTED, false);
        safety.setPadding(0, dp(15), 0, dp(12)); currentContent.addView(safety, bottom(dp(8)));
        Button source = button("VIEW SOURCE REPOSITORY   ↗", FG, PANEL_HI); currentContent.addView(source);
        source.setOnClickListener(v -> openUrl(app.repository));
    }

    private void fetchLatest(AppEntry app, TextView target) {
        io.execute(() -> {
            try {
                JSONObject release = getJson(app.releaseApi);
                String version = release.optString("tag_name", "release");
                String date = release.optString("published_at", "");
                JSONObject assetInfo = findApkAsset(release);
                String asset = assetInfo == null ? null : assetInfo.optString("name");
                String digest = assetInfo == null ? "" : assetInfo.optString("digest", "");
                String result = version + (date.isEmpty() ? "" : "  ·  " + date.substring(0, Math.min(10, date.length()))) +
                        (asset == null ? "  ·  No APK asset found" : "  ·  " + asset + (digest.startsWith("sha256:") ? "  ·  SHA-256 published" : "  ·  no published hash"));
                runOnUiThread(() -> { if (!isFinishing()) target.setText("LATEST RELEASE  /  " + result); });
            } catch (Exception ex) {
                runOnUiThread(() -> { if (!isFinishing()) target.setText("LATEST RELEASE  /  Could not load from GitHub. Check connection or repository releases."); });
            }
        });
    }

    private void promptAddSource() {
        EditText address = new EditText(this);
        address.setSingleLine(true); address.setTextSize(15); address.setTextColor(FG); address.setHintTextColor(MUTED);
        address.setHint("https://github.com/owner/repository");
        address.setPadding(dp(18), dp(10), dp(18), dp(10));
        new AlertDialog.Builder(this)
                .setTitle("Add from GitHub")
                .setMessage("Paste a public repository URL. This adds it to your device as an unlisted source; it is not curator-reviewed.")
                .setView(address)
                .setNegativeButton("Cancel", (dialog, which) -> {})
                .setPositiveButton("Check source", (dialog, which) -> checkUserSource(address.getText().toString()))
                .show();
    }

    private void checkUserSource(String address) {
        Uri uri = Uri.parse(address.trim());
        List<String> parts = uri.getPathSegments();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !"github.com".equalsIgnoreCase(uri.getHost()) || parts.size() != 2) {
            Toast.makeText(this, "Use a GitHub repository URL: github.com/owner/repository", Toast.LENGTH_LONG).show(); return;
        }
        String owner = parts.get(0), repository = parts.get(1).replaceFirst("\\.git$", "");
        String canonical = "https://github.com/" + owner + "/" + repository;
        for (AppEntry existing : catalog) if (existing.repository.equalsIgnoreCase(canonical)) {
            Toast.makeText(this, "That source is already in your catalog.", Toast.LENGTH_SHORT).show(); return;
        }
        Toast.makeText(this, "Checking repository and latest release…", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            try {
                JSONObject repositoryInfo = getJson("https://api.github.com/repos/" + owner + "/" + repository);
                JSONObject release = getJson("https://api.github.com/repos/" + owner + "/" + repository + "/releases/latest");
                if (findApkUrl(release) == null) throw new Exception("The latest release has no direct APK asset.");
                AppEntry app = new AppEntry();
                app.id = "user-" + owner + "-" + repository; app.packageId = "";
                app.name = repositoryInfo.optString("name", repository);
                JSONObject ownerInfo = repositoryInfo.optJSONObject("owner");
                app.developer = ownerInfo == null ? owner : ownerInfo.optString("login", owner);
                app.repository = canonical;
                app.releaseApi = "https://api.github.com/repos/" + owner + "/" + repository + "/releases/latest";
                app.category = "Community";
                app.summary = repositoryInfo.optString("description", "GitHub release app");
                if (app.summary == null || app.summary.trim().isEmpty()) app.summary = "GitHub release app";
                JSONObject license = repositoryInfo.optJSONObject("license");
                app.license = license == null ? "Not declared" : license.optString("spdx_id", "Not declared");
                app.platform = "Android (user-added)";
                app.inputSupport = "Not reviewed";
                app.curatorNote = "User-added source. Latest GitHub release: " + release.optString("tag_name", "unknown") + ".";
                app.unlisted = true;
                runOnUiThread(() -> {
                    catalog.add(app); saveUserSources(); activeCategory = "All"; query = ""; showHome();
                    Toast.makeText(this, app.name + " added as an unlisted source.", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Could not add source: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void saveUserSources() {
        JSONArray userApps = new JSONArray();
        for (AppEntry app : catalog) if (app.unlisted) userApps.put(app.toJson());
        getPreferences(MODE_PRIVATE).edit().putString("user_sources", userApps.toString()).apply();
    }

    private void installLatest(AppEntry app) {
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
            startActivity(settings); Toast.makeText(this, "Allow installs for DPAD Market, then choose install again.", Toast.LENGTH_LONG).show(); return;
        }
        Toast.makeText(this, "Checking GitHub release…", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            try {
                JSONObject release = getJson(app.releaseApi);
                JSONObject asset = findApkAsset(release);
                if (asset == null) throw new Exception("This release does not provide a direct APK asset.");
                String apkUrl = asset.optString("browser_download_url", null);
                String digest = asset.optString("digest", "");
                long size = asset.optLong("size", -1);
                if (size > 350L * 1024 * 1024) throw new Exception("APK asset exceeds the 350 MB safety limit.");
                installFromUrl(app, apkUrl, release.optString("tag_name", app.name), digest, size);
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Could not prepare install: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void installFromUrl(AppEntry app, String apkUrl, String version, String expectedDigest, long expectedSize) throws Exception {
        PackageInstaller installer = getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        if (!app.packageId.isEmpty()) params.setAppPackageName(app.packageId);
        int sessionId = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(sessionId)) {
            HttpURLConnection connection = (HttpURLConnection) new URL(apkUrl).openConnection();
            connection.setConnectTimeout(20000); connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", "DPAD-Market/0.1");
            connection.connect();
            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) throw new Exception("GitHub download returned HTTP " + connection.getResponseCode());
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            long total = 0;
            try (InputStream input = new BufferedInputStream(connection.getInputStream()); OutputStream output = session.openWrite("base.apk", 0, expectedSize)) {
                byte[] buffer = new byte[32768]; int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > 350L * 1024 * 1024) throw new Exception("APK download exceeds the 350 MB safety limit.");
                    sha256.update(buffer, 0, count); output.write(buffer, 0, count);
                }
                String actualDigest = toHex(sha256.digest());
                if (expectedSize >= 0 && total != expectedSize) throw new Exception("Downloaded file size does not match GitHub metadata.");
                if (expectedDigest.startsWith("sha256:") && !actualDigest.equalsIgnoreCase(expectedDigest.substring(7)))
                    throw new Exception("SHA-256 does not match GitHub's release asset digest. Install cancelled.");
                session.fsync(output);
            } finally { connection.disconnect(); }
            Intent status = new Intent(this, InstallStatusReceiver.class).setAction(InstallStatusReceiver.ACTION_INSTALL_STATUS);
            status.putExtra("app_name", app.name); status.putExtra("version", version);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent callback = PendingIntent.getBroadcast(this, sessionId, status, flags);
            session.commit(callback.getIntentSender());
            runOnUiThread(() -> Toast.makeText(this, "Download complete. Android will ask you to confirm installation.", Toast.LENGTH_LONG).show());
        } catch (Exception e) {
            installer.abandonSession(sessionId); throw e;
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }

    private String findApkUrl(JSONObject release) {
        JSONObject asset = findApkAsset(release);
        return asset == null ? null : asset.optString("browser_download_url", null);
    }

    private JSONObject findApkAsset(JSONObject release) {
        JSONArray assets = release.optJSONArray("assets"); if (assets == null) return null;
        JSONObject best = null; int bestScore = -1;
        String abi = Build.SUPPORTED_ABIS.length == 0 ? "" : Build.SUPPORTED_ABIS[0];
        for (int i = 0; i < assets.length(); i++) {
            JSONObject item = assets.optJSONObject(i); if (item == null) continue;
            String name = item.optString("name", ""); String lower = name.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".apk") || lower.contains("source") || lower.contains("symbols")) continue;
            int score = 0;
            if (lower.contains("universal")) score += 4;
            if (!abi.isEmpty() && lower.contains(abi.toLowerCase(Locale.ROOT))) score += 8;
            if (lower.contains("release")) score++;
            if (score > bestScore) { bestScore = score; best = item; }
        }
        return best;
    }

    private JSONObject getJson(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(15000); connection.setReadTimeout(20000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        connection.setRequestProperty("User-Agent", "DPAD-Market-Android");
        try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int n; while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
            return new JSONObject(output.toString("UTF-8"));
        } finally { connection.disconnect(); }
    }

    private void addMeta(String key, String value) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(0, dp(10), 0, dp(10));
        TextView k = text(key.toUpperCase(Locale.ROOT), 10, MUTED, true); row.addView(k);
        TextView v = text(value, 13, FG, false); row.addView(v);
        currentContent.addView(row); View line = new View(this); line.setBackgroundColor(RULE); currentContent.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
    }
    private void sectionLabel(LinearLayout parent, String label) { TextView t = text(label, 10, LIME, true); parent.addView(t, bottom(dp(8))); }
    private void addBack(LinearLayout parent, String label) { Button b = button(label, FG, PANEL_HI); parent.addView(b, bottom(dp(22))); b.setOnClickListener(v -> showHome()); }
    private void openUrl(String url) { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception e) { Toast.makeText(this, "No browser available", Toast.LENGTH_SHORT).show(); } }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setFocusable(false); return t;
    }
    private Button button(String label, int textColor, int backgroundColor) {
        Button b = new Button(this); b.setText(label); b.setTextSize(12); b.setTextColor(textColor); b.setAllCaps(false);
        b.setBackground(selectable(backgroundColor)); b.setMinHeight(dp(46)); b.setMinWidth(0); b.setMinimumWidth(0);
        b.setPadding(dp(13), 0, dp(13), 0); return b;
    }
    private android.graphics.drawable.Drawable selectable() { return selectable(PANEL); }
    private android.graphics.drawable.Drawable selectable(int color) {
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        android.graphics.drawable.GradientDrawable focused = new android.graphics.drawable.GradientDrawable(); focused.setColor(PANEL_HI); focused.setStroke(dp(2), LIME);
        android.graphics.drawable.GradientDrawable normal = new android.graphics.drawable.GradientDrawable(); normal.setColor(color); normal.setStroke(dp(1), RULE);
        states.addState(new int[]{android.R.attr.state_focused}, focused); states.addState(new int[]{android.R.attr.state_pressed}, focused); states.addState(new int[]{}, normal); return states;
    }
    private LinearLayout.LayoutParams bottom(int margin) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = margin; return p; }
    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && selected != null) { selected = null; showHome(); return true; }
        return super.onKeyDown(keyCode, event);
    }
    @Override protected void onDestroy() { super.onDestroy(); }

    static class AppEntry {
        String id, packageId, name, developer, repository, releaseApi, category, summary, curatorNote, license, platform, inputSupport;
        boolean unlisted;
        static AppEntry from(JSONObject o) {
            AppEntry a = new AppEntry(); a.id=o.optString("id"); a.packageId=o.optString("packageId"); a.name=o.optString("name"); a.developer=o.optString("developer");
            a.repository=o.optString("repository"); a.releaseApi=o.optString("releaseApi"); a.category=o.optString("category");
            a.summary=o.optString("summary"); a.curatorNote=o.optString("curatorNote"); a.license=o.optString("license");
            a.platform=o.optString("platform"); a.inputSupport=o.optString("inputSupport"); a.unlisted=o.optBoolean("unlisted", false); return a;
        }
        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id); o.put("packageId", packageId); o.put("name", name); o.put("developer", developer);
                o.put("repository", repository); o.put("releaseApi", releaseApi); o.put("category", category);
                o.put("summary", summary); o.put("curatorNote", curatorNote); o.put("license", license);
                o.put("platform", platform); o.put("inputSupport", inputSupport); o.put("unlisted", unlisted);
            } catch (Exception ignored) { }
            return o;
        }
    }
}
