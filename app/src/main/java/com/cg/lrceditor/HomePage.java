package com.cg.lrceditor;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HomePage extends AppCompatActivity implements HomePageListAdapter.LyricFileSelectListener {

    private String readLocation;
    private Uri readUri;

    private RecyclerView recyclerView;
    private HomePageListAdapter adapter;

    private ActionModeCallback actionModeCallback;
    private ActionMode actionMode;

    private SwipeRefreshLayout swipeRefreshLayout;

    private String currentTheme;

    private Toolbar toolbar;
    private MenuItem refreshItem;

    private boolean isDarkTheme = false;
    private boolean threadIsExecuting = false; // Variable to prevent multiple threading operations at once
    private boolean storagePermissionIsGranted = false;
    private boolean stopScanning = true;

    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        preferences = getSharedPreferences("LRC Editor Preferences", MODE_PRIVATE);
        String theme = preferences.getString("current_theme", "light");
        currentTheme = theme;
        if (theme.equals("dark")) {
            isDarkTheme = true;
            setTheme(R.style.AppThemeDark);
        } else if (theme.equals("darker")) {
            isDarkTheme = true;
            setTheme(R.style.AppThemeDarker);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_homepage);

        toolbar = findViewById(R.id.toolbar);
        if (isDarkTheme) {
            /* Dark toolbar popups for dark themes */
            toolbar.setPopupTheme(R.style.AppThemeDark_PopupOverlay);
        }
        setSupportActionBar(toolbar);

        if (isDarkTheme) {
            /* Switch to a light icon when using a dark theme */
            TextView emptyTextview = findViewById(R.id.empty_message_textview);
            emptyTextview.setCompoundDrawablesRelativeWithIntrinsicBounds(null, getDrawable(R.drawable.ic_thats_a_miss_light), null, null);
        }

        recyclerView = findViewById(R.id.recyclerview);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(recyclerView.getContext(),
                DividerItemDecoration.VERTICAL);
        recyclerView.addItemDecoration(dividerItemDecoration);
        try {
            ((SimpleItemAnimator) recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);
        } catch (NullPointerException e) {
            e.printStackTrace();
        }

        updateRecyclerviewAdapter();

        swipeRefreshLayout = findViewById(R.id.swiperefresh);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        if (threadIsExecuting) {
                            showToastOnUiThread(getString(R.string.another_operation_wait_message));
                            return;
                        }
                        threadIsExecuting = true;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                swipeRefreshLayout.setRefreshing(true);
                            }
                        });

                        scanLyrics();

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                swipeRefreshLayout.setRefreshing(false);
                            }
                        });
                        threadIsExecuting = false;
                    }
                }).start();
            }
        });

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(HomePage.this, CreateActivity.class);
                startActivity(intent);
            }
        });

        readLocation = preferences.getString("readLocation", Constants.defaultLocation);

        actionModeCallback = new ActionModeCallback();

        readyFileIO();
    }

    @Override
    protected void onResume() {
        super.onResume();

        String theme = preferences.getString("current_theme", "light");
        if (!theme.equals(currentTheme)) {
            recreate();
        }

        String oldReadLocation = readLocation;
        readLocation = preferences.getString("readLocation", Constants.defaultLocation);
        String uriString = preferences.getString("readUri", null);
        if (uriString != null) {
            readUri = Uri.parse(uriString);
        }

        if (storagePermissionIsGranted && !oldReadLocation.equals(readLocation)) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if (threadIsExecuting) {
                        showToastOnUiThread(getString(R.string.another_operation_refresh_failed_message));
                        return;
                    }
                    threadIsExecuting = true;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            swipeRefreshLayout.setRefreshing(true);
                        }
                    });

                    scanLyrics();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            swipeRefreshLayout.setRefreshing(false);
                        }
                    });
                    threadIsExecuting = false;
                }
            }).start();
        }
    }

    /* Takes care of everything to scan lyrics and display it */
    synchronized private void scanLyrics() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (actionMode != null) {
                    actionMode.finish();
                }
                actionMode = null;

                toolbar.collapseActionView();
            }
        });

        final File scanLocation = new File(readLocation);

        TextView textView;
        try {
            textView = findViewById(R.id.empty_message_textview);
        } catch (NullPointerException e) {
            showToastOnUiThread(getString(R.string.ui_reference_failed_message));
            return;
        }

        final TextView emptyTextview = textView;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateRecyclerviewAdapter();
            }
        });

        if (!scanLocation.exists()) {
            if (!scanLocation.mkdir()) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showReadLocationResetDialog(scanLocation);
                    }
                });
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    emptyTextview.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                }
            });

            return;
        }

        //showToastOnUiThread("Scanning for LRC files in the read location (and sub directories)");
        // This toast was too annoying...

        stopScanning = false;

        try {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    refreshItem.setIcon(getDrawable(R.drawable.ic_cancel_toolbar));

                    emptyTextview.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                }
            });

            scanDirectory(scanLocation);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (adapter != null) {
                        final int noOfItems = adapter.listData.size();
                        if (noOfItems <= 0) {
                            if (!stopScanning) {
                                Toast.makeText(getApplicationContext(), getString(R.string.no_lrc_files_found_message), Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getApplicationContext(), getString(R.string.scan_cancelled_message), Toast.LENGTH_SHORT).show();
                            }
                            emptyTextview.setVisibility(View.VISIBLE);
                            recyclerView.setVisibility(View.GONE);
                        } else if (!stopScanning) {
                            Toast.makeText(getApplicationContext(), getResources().getQuantityString(R.plurals.scanned_x_lrc_files_message, noOfItems, noOfItems), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getApplicationContext(), getString(R.string.scan_cancelled_message) + "; " + getResources().getQuantityString(R.plurals.scanned_x_lrc_files_message, noOfItems, noOfItems), Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });
        } catch (NullPointerException e) {
            e.printStackTrace();
            showToastOnUiThread(getString(R.string.failed_to_scan_lyrics_message));
            showToastOnUiThread(getString(R.string.send_a_bug_report_message));
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                stopScanning = true;
                refreshItem.setIcon(getDrawable(R.drawable.ic_refresh_toolbar));
            }
        });
    }

    /* Scans all directories and sub directories for LRC files and updates the recyclerview adapter */
    private void scanDirectory(File dir) {
        if (adapter == null) {
            showToastOnUiThread(getString(R.string.failed_to_fetch_adapter_message));
            return;
        }

        File[] fileList = dir.listFiles();
        Arrays.sort(fileList);

        ArrayList<File> dirList = new ArrayList<>();
        for (; ; ) {
            for (final File file : fileList) {
                if (stopScanning) {
                    return;
                }

                if (file.isDirectory()) {
                    dirList.add(file);
                } else if (file.getName().endsWith(".lrc")) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            adapter.listData.add(new HomePageListItem(file, null, null));
                            // TODO: Need to optimize this. Currently causes noticeable stuttering and skipped frames
                            // Due to `notifyItemInserted` being called multiple times in a small time interval
                            adapter.notifyItemInserted(adapter.listData.size() - 1);
                        }
                    });
                }
            }

            if (!dirList.isEmpty()) {
                fileList = dirList.remove(dirList.size() - 1).listFiles();
                Arrays.sort(fileList);
            } else {
                break;
            }
        }
    }

    /* Creates/Clears the adapter of the recyclerview */
    private void updateRecyclerviewAdapter() {
        if (adapter == null) {
            adapter = new HomePageListAdapter(this, isDarkTheme);
            recyclerView.setAdapter(adapter);
            adapter.setClickListener(this);
        } else {
            adapter.clearExpandedItems();
            adapter.listData.clear();
            adapter.backupListData.clear();
            adapter.notifyDataSetChanged();
        }
    }

    private void showReadLocationResetDialog(File scanLocation) {
        Toast.makeText(getApplicationContext(), getString(R.string.permission_check_message), Toast.LENGTH_LONG).show();

        if (!readLocation.equals(Constants.defaultLocation)) {
            new AlertDialog.Builder(HomePage.this)
                    .setMessage(getString(R.string.read_location_invalid_message, scanLocation.getAbsolutePath()))
                    .setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            SharedPreferences.Editor editor = preferences.edit();

                            editor.putString("readLocation", Constants.defaultLocation);
                            editor.apply();

                            readLocation = Constants.defaultLocation;
                            readUri = null;

                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    if (threadIsExecuting) {
                                        showToastOnUiThread(getString(R.string.another_operation_refresh_failed_message));
                                        return;
                                    }
                                    threadIsExecuting = true;
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            swipeRefreshLayout.setRefreshing(true);
                                        }
                                    });

                                    scanLyrics();

                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            swipeRefreshLayout.setRefreshing(false);
                                        }
                                    });
                                    threadIsExecuting = false;
                                }
                            }).start();
                        }
                    })
                    .setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Toast.makeText(getApplicationContext(), getString(R.string.lrc_editor_may_not_work_as_expected_message), Toast.LENGTH_LONG).show();
                        }
                    })
                    .setCancelable(false)
                    .create()
                    .show();
        } else {
            Toast.makeText(getApplicationContext(), getString(R.string.read_location_non_existent_message), Toast.LENGTH_LONG).show();
        }
    }

    private void readyFileIO() {
        if (!isExternalStorageWritable()) {
            Toast.makeText(this, getString(R.string.storage_unavailable_message), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || grantPermission()) /* Marshmallow onwards require runtime permissions */
            storagePermissionIsGranted = true;
    }

    private boolean grantPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            showPermissionDialog();
            return false;
        }

        return true;
    }

    private void showPermissionDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setMessage(getString(R.string.storage_permission_prompt));
        dialog.setTitle(getString(R.string.need_permissions));
        dialog.setCancelable(false);
        dialog.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ActivityCompat.requestPermissions(HomePage.this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, Constants.WRITE_EXTERNAL_REQUEST);
            }
        });
        dialog.show();

    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == Constants.WRITE_EXTERNAL_REQUEST) {
            for (int i = 0; i < permissions.length; i++) {
                String permission = permissions[i];
                int grantResult = grantResults[i];

                if (permission.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        storagePermissionIsGranted = true;
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                if (threadIsExecuting) {
                                    showToastOnUiThread(getString(R.string.another_operation_refresh_failed_message));
                                    return;
                                }
                                threadIsExecuting = true;
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        swipeRefreshLayout.setRefreshing(true);
                                    }
                                });

                                scanLyrics();

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        swipeRefreshLayout.setRefreshing(false);
                                    }
                                });
                                threadIsExecuting = false;
                            }
                        }).start();
                        return;
                    } else {
                        Toast.makeText(this, getString(R.string.no_permission_granted_message), Toast.LENGTH_LONG).show();
                        finish();
                    }
                }
            }
        }
    }

    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_homepage_activity, menu);

        refreshItem = menu.findItem(R.id.action_refresh);

        final MenuItem searchItem = menu.findItem(R.id.action_search);
        try {
            final SearchView searchView = (SearchView) searchItem.getActionView();
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String s) {
                    return false;
                }

                @Override
                public boolean onQueryTextChange(String s) {
                    if (actionMode != null) { // Glitches and crashes might happen when the user uses the search bar
                        // with selections while it is hidden behind the contextual menu bar
                        adapter.clearSelections();
                        actionMode.finish();
                    }
                    actionMode = null;

                    adapter.getFilter().filter(s);
                    return true;
                }
            });

            searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                @Override
                public boolean onMenuItemActionExpand(MenuItem menuItem) {
                    adapter.backupListData = adapter.listData; // Backup current data
                    return true;
                }

                @Override
                public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                    adapter.listData = adapter.backupListData; // Restore backed up data
                    recyclerView.swapAdapter(adapter, false);
                    return true;
                }
            });
        } catch (NullPointerException e) {
            Toast.makeText(this, getString(R.string.failed_to_initialize_search_message), Toast.LENGTH_SHORT).show();
            searchItem.setVisible(false);
        }

        if (storagePermissionIsGranted) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if (threadIsExecuting) {
                        showToastOnUiThread(getString(R.string.another_operation_refresh_failed_message));
                        return;
                    }
                    threadIsExecuting = true;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            swipeRefreshLayout.setRefreshing(true);
                        }
                    });

                    scanLyrics();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            swipeRefreshLayout.setRefreshing(false);
                        }
                    });
                    threadIsExecuting = false;
                }
            }).start();
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        Intent intent;
        switch (item.getItemId()) {
            case R.id.action_refresh:
                if (stopScanning) { // If this is true, scanning is not taking place; clicked on the refresh button
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if (threadIsExecuting) {
                                showToastOnUiThread(getString(R.string.another_operation_wait_message));
                                return;
                            }
                            threadIsExecuting = true;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    swipeRefreshLayout.setRefreshing(true);
                                }
                            });

                            scanLyrics();

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    swipeRefreshLayout.setRefreshing(false);
                                }
                            });
                            threadIsExecuting = false;
                        }
                    }).start();
                } else { // Scan is taking place; clicked on the cancel scan button
                    stopScanning = true;
                }
                return true;

            case R.id.action_settings:
                intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;

            case R.id.action_about:
                intent = new Intent(this, AboutActivity.class);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void showToastOnUiThread(final String str) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), str, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void fileSelected(String fileLocation, final String fileName) {
        final LyricReader r = new LyricReader(fileLocation, fileName, this);
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (threadIsExecuting) {
                    showToastOnUiThread(getString(R.string.another_operation_wait_message));
                    return;
                }
                threadIsExecuting = true;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        swipeRefreshLayout.setRefreshing(true);
                    }
                });

                if (r.getErrorMsg() != null || !r.readLyrics()) {
                    showToastOnUiThread(r.getErrorMsg());
                } else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Intent intent = new Intent(getApplicationContext(), EditorActivity.class);
                            intent.putExtra("LYRICS", r.getLyrics());
                            intent.putExtra("TIMESTAMPS", r.getTimestamps());
                            intent.putExtra("SONG METADATA", r.getSongMetaData());
                            intent.putExtra("LRC FILE NAME", fileName);

                            startActivity(intent);
                        }
                    });
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                });
                threadIsExecuting = false;
            }
        }).start();
    }

    @Override
    public void onLyricItemSelected(int position) {
        if (actionMode == null) {
            actionMode = startSupportActionMode(actionModeCallback);
        }

        toggleSelection(position);
    }

    @Override
    public void onLyricItemClicked(int position) {
        if (actionMode == null)
            return;

        toggleSelection(position);
    }

    private void toggleSelection(int position) {
        adapter.toggleSelection(position);

        checkActionModeItems();
    }

    private void checkActionModeItems() {
        int count = adapter.getSelectionCount();

        if (count == 0) {
            if (actionMode != null) {
                actionMode.finish();
            }
            actionMode = null;
        } else {
            Menu menu = actionMode.getMenu();
            MenuItem itemRename = menu.findItem(R.id.action_rename_homepage);
            if (count >= 2) {
                itemRename.setVisible(false);
            } else {
                itemRename.setVisible(true);
            }

            actionMode.setTitle(String.valueOf(count));
            actionMode.invalidate();
        }
    }

    private void deleteLyricFiles() {
        if (threadIsExecuting) {
            Toast.makeText(this, getString(R.string.another_operation_wait_message), Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.confirmation))
                .setMessage(getString(R.string.delete_confirmation))
                .setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final List<HomePageListItem> itemsToDelete = new ArrayList<>();
                        for (int i : adapter.getSelectedItemIndices()) {
                            itemsToDelete.add(adapter.listData.get(i));
                        }

                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                if (threadIsExecuting) {
                                    showToastOnUiThread(getString(R.string.another_operation_wait_message));
                                    return;
                                }
                                threadIsExecuting = true;
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        swipeRefreshLayout.setRefreshing(true);
                                    }
                                });

                                showToastOnUiThread(getString(R.string.deleting_message));

                                DocumentFile pickedDir = FileUtil.getPersistableDocumentFile(readUri, readLocation, getApplicationContext());

                                boolean deleteFailure = false;

                                while (!itemsToDelete.isEmpty()) {
                                    final HomePageListItem currentItem = itemsToDelete.remove(itemsToDelete.size() - 1);
                                    File f = currentItem.file;
                                    final String location = FileUtil.stripFileNameFromPath(f.getAbsolutePath());

                                    DocumentFile file = FileUtil.searchForFileOptimized(pickedDir, location, f.getName(), getApplicationContext().getExternalFilesDirs(null));
                                    if (file == null || !file.delete()) {
                                        deleteFailure = true;
                                    } else {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (toolbar.hasExpandedActionView()) {
                                                    adapter.backupListData.remove(currentItem);
                                                }

                                                int index = adapter.listData.indexOf(currentItem);
                                                if (index != -1) {
                                                    adapter.listData.remove(currentItem);
                                                    adapter.notifyItemRemoved(index);
                                                }

                                                checkActionModeItems();
                                            }
                                        });
                                    }
                                }

                                final boolean finalDeleteFailure = deleteFailure;
                                if (finalDeleteFailure) {
                                    showToastOnUiThread(getString(R.string.delete_failed_message));
                                } else {
                                    showToastOnUiThread(getString(R.string.delete_successful_message));
                                }

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        swipeRefreshLayout.setRefreshing(false);
                                    }
                                });

                                threadIsExecuting = false;
                            }
                        }).start();
                    }
                })
                .setNegativeButton(getString(R.string.no), null)
                .create()
                .show();

    }

    private void renameLyricFile() {
        if (threadIsExecuting) {
            Toast.makeText(this, getString(R.string.another_operation_wait_message), Toast.LENGTH_SHORT).show();
            return;
        }

        LayoutInflater inflater = this.getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_edit, null);
        final EditText editText = view.findViewById(R.id.dialog_edittext);
        TextView textView = view.findViewById(R.id.dialog_prompt);

        final File f = adapter.listData.get(adapter.getSelectedItemIndices().get(0)).file;

        String fileName = f.getName();
        textView.setText(getString(R.string.new_file_name_prompt));
        editText.setText(fileName);

        editText.setHint(getString(R.string.new_file_name_hint));

        new AlertDialog.Builder(this)
                .setView(view)
                .setPositiveButton(getString(R.string.rename), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final HomePageListItem itemToRename = adapter.listData.get(adapter.getSelectedItemIndices().get(0));

                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                if (threadIsExecuting) {
                                    showToastOnUiThread(getString(R.string.another_operation_wait_message));
                                    return;
                                }
                                threadIsExecuting = true;
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        swipeRefreshLayout.setRefreshing(true);
                                    }
                                });

                                showToastOnUiThread(getString(R.string.renaming_message));

                                final String newName = editText.getText().toString();

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (actionMode != null) {
                                            actionMode.finish();
                                        }
                                        actionMode = null;
                                    }
                                });

                                DocumentFile pickedDir = FileUtil.getPersistableDocumentFile(readUri, readLocation, getApplicationContext());
                                final String location = FileUtil.stripFileNameFromPath(f.getAbsolutePath());

                                if (new File(location, newName).exists()) {
                                    showToastOnUiThread(getString(R.string.file_name_already_exists_message));
                                }

                                DocumentFile file = FileUtil.searchForFileOptimized(pickedDir, location, f.getName(), getApplicationContext().getExternalFilesDirs(null));

                                if (file != null && file.renameTo(newName)) {
                                    showToastOnUiThread(getString(R.string.rename_successful_message));
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            int index = adapter.listData.indexOf(itemToRename);
                                            if (index != -1) {
                                                adapter.listData.get(index).file = new File(location, newName);
                                                adapter.notifyItemChanged(index);
                                            }

                                            if (toolbar.hasExpandedActionView()) {
                                                int index2 = adapter.backupListData.indexOf(itemToRename);
                                                if (index != -1) {
                                                    adapter.backupListData.get(index2).file = adapter.listData.get(index).file;
                                                } else {
                                                    adapter.backupListData.get(index2).file = new File(location, newName);
                                                }
                                            }
                                        }
                                    });
                                } else {
                                    showToastOnUiThread(getString(R.string.rename_failed_message));
                                }

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        swipeRefreshLayout.setRefreshing(false);
                                    }
                                });
                                threadIsExecuting = false;
                            }
                        }).start();
                    }
                }).setNegativeButton(getString(R.string.cancel), null)
                .create()
                .show();
    }

    private void selectAll() {
        adapter.selectAll();
        int count = adapter.getSelectionCount();

        if (count >= 2)
            actionMode.getMenu().findItem(R.id.action_rename_homepage).setVisible(false);

        actionMode.setTitle(String.valueOf(count));
        actionMode.invalidate();
    }

    private class ActionModeCallback implements ActionMode.Callback {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.contextual_menu_homepage_activity, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_delete_homepage:
                    deleteLyricFiles();
                    return true;

                case R.id.action_rename_homepage:
                    renameLyricFile();
                    return true;

                case R.id.action_select_all_homepage:
                    selectAll();
                    return true;

                default:
                    return false;
            }
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            adapter.clearSelections();
            actionMode = null;
        }
    }
}
