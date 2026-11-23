package com.example.android.notepad;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Displays a list of notes. Will display notes from the {@link Uri}
 * provided in the incoming Intent if there is one, otherwise it defaults to displaying the
 * contents of the {@link NotePadProvider}.
 *
 * Extensions over the original sample:
 * - every item shows the timestamp of the note;
 * - notes can be searched by title or content;
 * - notes can be filtered by category;
 * - the whole app can be switched between day and night themes.
 */
public class NotesList extends AppCompatActivity {

    // For logging and debugging
    private static final String TAG = "NotesList";

    /**
     * The columns needed by the cursor adapter
     */
    private static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID,                          // 0
            NotePad.Notes.COLUMN_NAME_TITLE,            // 1
            NotePad.Notes.COLUMN_NAME_NOTE,             // 2
            NotePad.Notes.COLUMN_NAME_CATEGORY,         // 3
            NotePad.Notes.COLUMN_NAME_CREATE_DATE,      // 4
    };

    /** The index of the title column */
    private static final int COLUMN_INDEX_TITLE = 1;

    private final SimpleDateFormat mTimeFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    private ListView mNoteList;
    private TextView mEmptyView;
    private SimpleCursorAdapter mAdapter;

    /** Current search keyword. Empty means no keyword. */
    private String mQuery = "";

    /** Current category filter. Null means all categories. */
    private String mCategory = null;

    private Uri mNotesUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // The user does not need to hold down the key to use menu shortcuts.
        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

        /* If no data is given in the Intent that started this Activity, then this Activity
         * was started when the intent filter matched a MAIN action. We should use the default
         * provider URI.
         */
        Intent intent = getIntent();
        if (intent.getData() == null) {
            intent.setData(NotePad.Notes.CONTENT_URI);
        }
        mNotesUri = intent.getData();

        setContentView(R.layout.notes_list);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mNoteList = findViewById(R.id.note_list);
        mEmptyView = findViewById(R.id.empty_view);

        // The names of the cursor columns to display in the view.
        String[] dataColumns = {
                NotePad.Notes.COLUMN_NAME_TITLE,
                NotePad.Notes.COLUMN_NAME_NOTE,
                NotePad.Notes.COLUMN_NAME_CATEGORY,
                NotePad.Notes.COLUMN_NAME_CREATE_DATE
        };

        // The view IDs that will display the cursor columns.
        int[] viewIDs = {
                R.id.note_title,
                R.id.note_snippet,
                R.id.note_category,
                R.id.note_time
        };

        mAdapter = new SimpleCursorAdapter(this, R.layout.noteslist_item, null,
                dataColumns, viewIDs, 0);

        // Formats the timestamp and the short preview of every note.
        mAdapter.setViewBinder((view, cursor, columnIndex) -> {
            TextView textView = (TextView) view;
            String columnName = cursor.getColumnName(columnIndex);

            if (NotePad.Notes.COLUMN_NAME_CREATE_DATE.equals(columnName)) {
                long time = cursor.getLong(columnIndex);
                textView.setText(getString(R.string.created_at, mTimeFormat.format(new Date(time))));
                return true;
            }
            if (NotePad.Notes.COLUMN_NAME_NOTE.equals(columnName)) {
                String note = cursor.getString(columnIndex);
                note = note == null ? "" : note.replace('\n', ' ').trim();
                textView.setText(note);
                textView.setVisibility(note.isEmpty() ? View.GONE : View.VISIBLE);
                return true;
            }
            if (NotePad.Notes.COLUMN_NAME_CATEGORY.equals(columnName)) {
                String category = cursor.getString(columnIndex);
                textView.setText(TextUtils.isEmpty(category)
                        ? NotePad.Notes.DEFAULT_CATEGORY : category);
                return true;
            }
            return false;
        });

        mNoteList.setAdapter(mAdapter);
        mNoteList.setOnCreateContextMenuListener(this);
        mNoteList.setOnItemClickListener((parent, view, position, id) -> openNote(id));

        FloatingActionButton addButton = findViewById(R.id.fab_add);
        addButton.setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_INSERT, mNotesUri)));

        ChipGroup categoryChips = findViewById(R.id.category_chips);
        categoryChips.setOnCheckedStateChangeListener(
                (@NonNull ChipGroup group, @NonNull List<Integer> checkedIds) -> {
                    if (checkedIds.isEmpty()) {
                        mCategory = null;
                    } else {
                        Chip chip = group.findViewById(checkedIds.get(0));
                        String label = chip.getText().toString();
                        mCategory = getString(R.string.category_all).equals(label) ? null : label;
                    }
                    refreshList();
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh so that notes added / edited / deleted in NoteEditor show up immediately.
        refreshList();
    }

    /**
     * Queries the provider using the current keyword and category filter and swaps the cursor.
     */
    private void refreshList() {
        StringBuilder selection = new StringBuilder();
        ArrayList<String> selectionArgs = new ArrayList<>();

        if (mCategory != null) {
            selection.append(NotePad.Notes.COLUMN_NAME_CATEGORY).append(" = ?");
            selectionArgs.add(mCategory);
        }

        if (!TextUtils.isEmpty(mQuery)) {
            if (selection.length() > 0) {
                selection.append(" AND ");
            }
            selection.append("(")
                    .append(NotePad.Notes.COLUMN_NAME_TITLE).append(" LIKE ?")
                    .append(" OR ")
                    .append(NotePad.Notes.COLUMN_NAME_NOTE).append(" LIKE ?")
                    .append(")");
            String like = "%" + mQuery + "%";
            selectionArgs.add(like);
            selectionArgs.add(like);
        }

        Cursor cursor = getContentResolver().query(
                mNotesUri,
                PROJECTION,
                selection.length() == 0 ? null : selection.toString(),
                selectionArgs.isEmpty() ? null : selectionArgs.toArray(new String[0]),
                NotePad.Notes.DEFAULT_SORT_ORDER);

        mAdapter.changeCursor(cursor);
        mEmptyView.setVisibility(
                cursor == null || cursor.getCount() == 0 ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_options_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.menu_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        if (searchView != null) {
            searchView.setQueryHint(getString(R.string.menu_search_hint));
            searchView.setMaxWidth(Integer.MAX_VALUE);
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    mQuery = query == null ? "" : query.trim();
                    refreshList();
                    searchView.clearFocus();
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    mQuery = newText == null ? "" : newText.trim();
                    refreshList();
                    return true;
                }
            });
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        // The paste menu item is enabled only if there is data on the clipboard.
        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);
        MenuItem pasteItem = menu.findItem(R.id.menu_paste);
        if (pasteItem != null) {
            pasteItem.setEnabled(clipboard != null && clipboard.hasPrimaryClip());
        }

        // Show the action that will be performed, not the current theme.
        MenuItem themeItem = menu.findItem(R.id.menu_theme);
        if (themeItem != null) {
            themeItem.setTitle(isNightMode()
                    ? R.string.menu_theme_light : R.string.menu_theme_night);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_add) {
            startActivity(new Intent(Intent.ACTION_INSERT, mNotesUri));
            return true;
        } else if (id == R.id.menu_paste) {
            startActivity(new Intent(Intent.ACTION_PASTE, mNotesUri));
            return true;
        } else if (id == R.id.menu_theme) {
            toggleNightMode();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean isNightMode() {
        int nightMode = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    private void toggleNightMode() {
        int mode = isNightMode()
                ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES;
        getSharedPreferences(NotePadApp.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putInt(NotePadApp.PREF_NIGHT_MODE, mode)
                .apply();
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return;
        }

        Cursor cursor = (Cursor) mAdapter.getItem(info.position);
        if (cursor == null) {
            return;
        }

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_context_menu, menu);
        menu.setHeaderTitle(cursor.getString(COLUMN_INDEX_TITLE));
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return false;
        }

        Uri noteUri = ContentUris.withAppendedId(mNotesUri, info.id);
        int id = item.getItemId();
        if (id == R.id.context_open) {
            startActivity(new Intent(Intent.ACTION_EDIT, noteUri));
            return true;
        } else if (id == R.id.context_copy) {
            ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newUri(getContentResolver(), "Note", noteUri));
            return true;
        } else if (id == R.id.context_delete) {
            getContentResolver().delete(noteUri, null, null);
            refreshList();
            return true;
        }
        return super.onContextItemSelected(item);
    }

    /**
     * Opens a note, or returns it to the caller when this activity was started for a result.
     */
    private void openNote(long id) {
        Uri uri = ContentUris.withAppendedId(mNotesUri, id);
        String action = getIntent().getAction();

        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
            setResult(RESULT_OK, new Intent().setData(uri));
            finish();
        } else {
            startActivity(new Intent(Intent.ACTION_EDIT, uri));
        }
    }
}
