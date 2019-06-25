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
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.provider.DocumentFile;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.SimpleItemAnimator;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HomePage extends AppCompatActivity implements HomePageListAdapter.LyricFileSelectListener {

    private boolean storagePermissionIsGranted = false;

    private String readLocation;
    private Uri readUri;

    private RecyclerView recyclerView;
    private HomePageListAdapter adapter;

    private ActionModeCallback actionModeCallback;
    private ActionMode actionMode;

    private SwipeRefreshLayout swipeRefreshLayout;

    private String currentTheme = "light";

    private boolean isDarkTheme = false;
    private boolean threadIsExecuting = false; // Variable to prevent multiple threading operations at once

    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        preferences = getSharedPreferences("LRC Editor Preferences", MODE_PRIVATE);
        String theme = preferences.getString("current_theme", "light");
        if (theme.equals("dark")) {
            isDarkTheme = true;
            currentTheme = "dark";
            setTheme(R.style.AppThemeDark);
        } else if (theme.equals("darker")) {
            isDarkTheme = true;
            currentTheme = "darker";
            setTheme(R.style.AppThemeDarker);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_homepage);

        Toolbar toolbar = findViewById(R.id.toolbar);
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
                            showToastOnUiThread("Another operation is running. Please wait until it completes");
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
        if (storagePermissionIsGranted) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if (threadIsExecuting) {
                        showToastOnUiThread("Another operation is running. Please wait until it completes");
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
                        showToastOnUiThread("Another operation is running. Couldn't refresh the list");
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
                    actionMode = null;
                }

                Toolbar toolbar = findViewById(R.id.toolbar);
                toolbar.collapseActionView();
            }
        });

        final File scanLocation = new File(readLocation);

        final TextView emptyTextview = findViewById(R.id.empty_message_textview);

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

        showToastOnUiThread("Scanning for LRC files in the read location (and sub directories)");

        try {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
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
                            Toast.makeText(getApplicationContext(), "No LRC files found", Toast.LENGTH_SHORT).show();
                            emptyTextview.setVisibility(View.VISIBLE);
                            recyclerView.setVisibility(View.GONE);
                        } else {
                            Toast.makeText(getApplicationContext(), "Scanned " + noOfItems + " LRC files", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });
        } catch (NullPointerException e) {
            e.printStackTrace();
            showToastOnUiThread("Failed to scan lyrics! Try changing the read location");
            showToastOnUiThread("Please send a bug report with as much detail as possible");
        }

    }

    /* Scans all directories and sub directories for LRC files and updates the recyclerview adapter */
    private void scanDirectory(File dir) {
        if (adapter == null) {
            showToastOnUiThread("Failed to fetch the adapter of the recyclerview");
            return;
        }

        File[] fileList = dir.listFiles();
        Arrays.sort(fileList);

        ArrayList<File> dirList = new ArrayList<>();
        for (; ; ) {
            for (final File file : fileList) {
                if (file.isDirectory()) {
                    dirList.add(file);
                } else if (file.getName().endsWith(".lrc")) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            adapter.listData.add(new HomePageListItem(file, null, null));
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
            adapter = new HomePageListAdapter(this);
            adapter.isDarkTheme = isDarkTheme;
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
        Toast.makeText(getApplicationContext(), "Make sure you have granted permissions", Toast.LENGTH_LONG).show();

        if (!readLocation.equals(Constants.defaultLocation)) {
            new AlertDialog.Builder(HomePage.this)
                    .setMessage("Read location doesn't exist. Tried to create a folder at '" + scanLocation.getAbsolutePath() + "' but failed. " +
                            "Do you want to reset the read location to the default location?")
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
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
                                        showToastOnUiThread("Another operation is running. Couldn't refresh the list");
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
                    .setNegativeButton("No", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Toast.makeText(getApplicationContext(), "LRC Editor may not work as expected; Try changing the read location from the settings", Toast.LENGTH_LONG).show();
                        }
                    })
                    .setCancelable(false)
                    .create()
                    .show();
        } else {
            Toast.makeText(getApplicationContext(), "Read location doesn't exist. Failed to create a 'Lyrics' folder as well. Try changing the read location", Toast.LENGTH_LONG).show();
        }
    }

    private void readyFileIO() {
        if (!isExternalStorageWritable()) {
            Toast.makeText(this, "ERROR: Storage unavailable/busy", Toast.LENGTH_LONG).show();
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
        dialog.setMessage("This app needs the storage permission for reading and saving the lyric files");
        dialog.setTitle("Need permissions");
        dialog.setCancelable(false);
        dialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
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
                                    showToastOnUiThread("Another operation is running. Couldn't refresh the list");
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
                        Toast.makeText(this, "LRC Editor cannot read/save lyric files without the storage permission", Toast.LENGTH_LONG).show();
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
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_homepage_activity, menu);

        final MenuItem searchItem = menu.findItem(R.id.action_search);
        final SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String s) {
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
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        if (threadIsExecuting) {
                            showToastOnUiThread("Another operation is running. Please wait until it completes");
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
    public void fileSelected(String fileLocation, String fileName) {
        LyricReader r = new LyricReader(fileLocation, fileName);
        if (r.getErrorMsg() != null || !r.readLyrics()) {
            Toast.makeText(this, r.getErrorMsg(), Toast.LENGTH_LONG).show();
            return;
        }

        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra("LYRICS", r.getLyrics());
        intent.putExtra("TIMESTAMPS", r.getTimestamps());
        intent.putExtra("SONG METADATA", r.getSongMetaData());
        intent.putExtra("LRC FILE NAME", fileName);

        startActivity(intent);
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
            actionMode.finish();
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
            Toast.makeText(this, "Another operation is running. Please wait until it completes", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Confirmation")
                .setMessage("Are you sure you want to delete the selected LRC files?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final List<Integer> selectedItemPositions =
                                adapter.getSelectedItemIndices();

                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                if (threadIsExecuting) {
                                    showToastOnUiThread("Another operation is running. Please wait until it completes");
                                    return;
                                }
                                threadIsExecuting = true;
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        swipeRefreshLayout.setRefreshing(true);
                                    }
                                });

                                showToastOnUiThread("Deleting... This may take a while depending on how far the file(s) are from the set read location");

                                DocumentFile pickedDir = FileUtil.getPersistableDocumentFile(readUri, readLocation, getApplicationContext());

                                boolean deleteFailure = false;

                                for (int i = selectedItemPositions.size() - 1; i >= 0; i--) {
                                    final int fileListIndex = selectedItemPositions.get(i);
                                    File f = adapter.listData.get(fileListIndex).file;
                                    final String location = FileUtil.stripFileNameFromPath(f.getAbsolutePath());

                                    DocumentFile file = FileUtil.searchForFileOptimized(pickedDir, location, f.getName(), getApplicationContext().getExternalFilesDirs(null));
                                    if (file == null || !file.delete()) {
                                        deleteFailure = true;
                                    } else {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toolbar toolbar = findViewById(R.id.toolbar);
                                                if (toolbar.hasExpandedActionView()) {
                                                    adapter.backupListData.remove(adapter.listData.get(fileListIndex));
                                                }

                                                adapter.listData.remove(fileListIndex);
                                                adapter.notifyItemRemoved(fileListIndex);

                                                checkActionModeItems();
                                            }
                                        });
                                    }
                                }

                                final boolean finalDeleteFailure = deleteFailure;
                                if (finalDeleteFailure) {
                                    showToastOnUiThread("Failed to delete some/all of the selected LRC files!");
                                } else {
                                    showToastOnUiThread("Deleted the selected LRC files successfully");
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
                .setNegativeButton("No", null)
                .create()
                .show();

    }

    private void renameLyricFile() {
        if (threadIsExecuting) {
            Toast.makeText(this, "Another operation is running. Please wait until it completes", Toast.LENGTH_SHORT).show();
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
                .setPositiveButton("Rename", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                if (threadIsExecuting) {
                                    showToastOnUiThread("Another operation is running. Please wait until it completes");
                                    return;
                                }
                                threadIsExecuting = true;
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        swipeRefreshLayout.setRefreshing(true);
                                    }
                                });

                                showToastOnUiThread("Renaming... This may take a while depending on how far the file is from the set read location");

                                final String newName = editText.getText().toString();
                                final int selectedItemIndex = adapter.getSelectedItemIndices().get(0);

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            actionMode.finish();
                                        } catch (NullPointerException e) {
                                            e.printStackTrace();
                                        }
                                        actionMode = null;
                                    }
                                });

                                DocumentFile pickedDir = FileUtil.getPersistableDocumentFile(readUri, readLocation, getApplicationContext());
                                final String location = FileUtil.stripFileNameFromPath(f.getAbsolutePath());

                                if (new File(location, newName).exists()) {
                                    showToastOnUiThread("File name already exists. Prefix might be added");
                                }

                                DocumentFile file = FileUtil.searchForFileOptimized(pickedDir, location, f.getName(), getApplicationContext().getExternalFilesDirs(null));

                                if (file != null && file.renameTo(newName)) {
                                    showToastOnUiThread("Renamed file successfully");
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            adapter.listData.get(selectedItemIndex).file = new File(location, newName);
                                            adapter.notifyItemChanged(selectedItemIndex);

                                            Toolbar toolbar = findViewById(R.id.toolbar);
                                            if (toolbar.hasExpandedActionView()) {
                                                int index = adapter.backupListData.indexOf(adapter.listData.get(selectedItemIndex));
                                                adapter.backupListData.get(index).file = adapter.listData.get(selectedItemIndex).file;
                                            }
                                        }
                                    });
                                } else {
                                    showToastOnUiThread("Rename failed!");
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
                }).setNegativeButton("Cancel", null)
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
