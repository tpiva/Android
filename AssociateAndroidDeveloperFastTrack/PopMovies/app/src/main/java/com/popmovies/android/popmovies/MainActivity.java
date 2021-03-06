/*
 * Copyright (C) 2017 Thiago Piva Magalhães
 */

package com.popmovies.android.popmovies;

import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.databinding.DataBindingUtil;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.popmovies.android.popmovies.adapters.MovieAdapter;
import com.popmovies.android.popmovies.bo.Movie;
import com.popmovies.android.popmovies.data.PopMoviesPreferences;
import com.popmovies.android.popmovies.databinding.ActivityMainBinding;
import com.popmovies.android.popmovies.db.PopMoviesContract;
import com.popmovies.android.popmovies.webservice.RequestMovies;

import java.util.ArrayList;
import java.util.List;

/**
 * Main activity to show grid with movies, start communication with server to get movies and
 * handler menu actions.
 */

public class MainActivity extends AppCompatActivity implements MovieAdapter.OnItemClickListener,
        LoaderManager.LoaderCallbacks<List<Movie>>{

    // COMPLETED fix saveInstance
    // COMPLETED fix error of duplicated favorite during back key
    private static final int POP_MOVIES_LOADER_ID = 120;

    private static final String SEARCH_CHANGED = "search_changed";
    private static final String CURRENT_SEARCH = "current_search";
    private static final String CURRENT_PAGE = "current_page";
    private static final String CURRENT_STATE_RV = "current_state_rv";

    public static final String SEARCH_TYPE_POPULAR = "popular";
    private static final String SEARCH_TYPE_FAVORITES = "favorites";

    private ArrayList<Movie> mCurrentMovies = new ArrayList<>();
    private GridLayoutManager mGridLayoutManager;
    private MovieAdapter mAdapter;
    private ActivityMainBinding mBinding;
    private ProgressDialog mProgressDialog;

    private int mActualPage = 1;
    private boolean isFetching = false;

    private static String sCurrentSearchType = "";

    // List of favorite movies, necessary to show what movie is favorite or not.
    private List<Integer> mFavoriteMoviesId = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_main);

        // get number of columns based on orientation / size
        int numberColumns = getResources().getInteger(R.integer.number_of_columns_grid_movies);
        mGridLayoutManager = new GridLayoutManager(this, numberColumns);

        mBinding.rcGridMovies.setLayoutManager(mGridLayoutManager);

        mAdapter = new MovieAdapter(this);
        mBinding.rcGridMovies.setAdapter(mAdapter);

        if (!Utility.isOnline(this)) {
            showMessageError();
        } else {
            if (savedInstanceState != null) {
                sCurrentSearchType = savedInstanceState.getString(CURRENT_SEARCH);
                mActualPage = savedInstanceState.getInt(CURRENT_PAGE);
                mBinding.rcGridMovies.getLayoutManager()
                        .onRestoreInstanceState(savedInstanceState.getParcelable(CURRENT_STATE_RV));
            }
        }

        // after end of recycle view load more movies from server side.
        mBinding.rcGridMovies.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0) {
                    int visibleItens = mGridLayoutManager.getChildCount();
                    int totalItens = mGridLayoutManager.getItemCount();
                    int firstVisible = mGridLayoutManager.findFirstVisibleItemPosition();

                    if (!SEARCH_TYPE_FAVORITES.equals(sCurrentSearchType)
                        && (firstVisible + visibleItens) >= totalItens && !isFetching) {
                        getSupportLoaderManager().restartLoader(POP_MOVIES_LOADER_ID, null, MainActivity.this);
                        mActualPage++;
                    }
                }
            }
        });

    }

    /**
     * Show progressBar to user to inform more movies are loading.
     */
    private void showProgress() {
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(this);
        }
        mProgressDialog.setMessage(getString(R.string.fetch_movies_loading));
        mProgressDialog.show();

        mBinding.tvMessageErrorLoading.setVisibility(View.INVISIBLE);
    }

    /**
     * Shows content of grid (movies) and turn invisible other UI elements.
     */
    private void showContent() {
        if (!mBinding.rcGridMovies.isShown()) {
            mBinding.tvMessageErrorLoading.setVisibility(View.VISIBLE);
        }

        if (mProgressDialog != null) {
            mProgressDialog.hide();
            mProgressDialog = null;
        }
        mBinding.tvMessageErrorLoading.setVisibility(View.INVISIBLE);
    }

    /**
     * Display error message during loading movies from server.
     */
    private void showMessageError() {
        if (mProgressDialog != null) {
            mProgressDialog.hide();
            mProgressDialog = null;
        }
        mBinding.tvMessageErrorLoading.setVisibility(View.VISIBLE);
    }

    /**
     * Display message for no favorites found
     */
    private void showNoFavoritesFoundMessage() {
        if (mProgressDialog != null) {
            mProgressDialog.hide();
            mProgressDialog = null;
        }

        mBinding.tvMessageErrorLoading.setText(getString(R.string.empty_favorite));
        mBinding.tvMessageErrorLoading.setVisibility(View.VISIBLE);
    }

    @Override
    public void onClicked(Movie movie) {
        Class targetClass = DetailMovieActivity.class;
        Intent detailIntent = new Intent(this, targetClass);
        detailIntent.putExtra(DetailMovieActivity.EXTRA_MOVIE, movie);
        startActivity(detailIntent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            startActivity(settingsIntent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(CURRENT_SEARCH, sCurrentSearchType);
        outState.putInt(CURRENT_PAGE, mActualPage);
        outState.putParcelable(CURRENT_STATE_RV,
                mGridLayoutManager.onSaveInstanceState());
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mGridLayoutManager.onRestoreInstanceState(savedInstanceState.getParcelable(CURRENT_STATE_RV));
        sCurrentSearchType = savedInstanceState.getString(CURRENT_SEARCH);
        mActualPage = savedInstanceState.getInt(CURRENT_PAGE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // if not fetching and preference search type changes reloads loader.
        String prefSearchType = PopMoviesPreferences.getSearchType(this);
        if (!isFetching
                && !sCurrentSearchType.equals(prefSearchType)) {
            sCurrentSearchType = prefSearchType;
            Bundle bundle = new Bundle();
            bundle.putBoolean(SEARCH_CHANGED, true);
            //restart loader
            getSupportLoaderManager().restartLoader(POP_MOVIES_LOADER_ID, bundle, this);
        } else if (!isFetching){
            getSupportLoaderManager().initLoader(POP_MOVIES_LOADER_ID, null, this);
        }
    }

    @Override
    public Loader<List<Movie>> onCreateLoader(int id, final Bundle args) {
        return new AsyncTaskLoader<List<Movie>>(this) {
            final String[] MOVIE_COLUMNS = {
                    PopMoviesContract.COLUMN_OVERVIEW,
                    PopMoviesContract.COLUMN_RELEASE_DATE,
                    PopMoviesContract.COLUMN_ORIGINAL_TITLE,
                    PopMoviesContract.COLUMN_VOTE_AVERAGE,
                    PopMoviesContract.COLUMN_VOTE_COUNT,
                    PopMoviesContract.COLUMN_POSTER,
                    PopMoviesContract.COLUMN_MOVIE_ID
            };

            static final int COL_MOVIE_OVERVIEW = 0;
            static final int COL_MOVIE_RELEASE_DATE = 1;
            static final int COL_MOVIE_ORIGINAL_TITLE = 2;
            static final int COL_MOVIE_VOTE_AVERAGE = 3;
            static final int COL_MOVIE_VOTE_COUNT = 4;
            static final int COL_MOVIE_POSTER = 5;
            static final int COL_MOVIE_MOVIE_ID = 6;

            @Override
            protected void onStartLoading() {
                isFetching = true;
                if (args != null) {
                    if (args.getBoolean(SEARCH_CHANGED)) {
                        // reset data, in case of change search type from preference.
                        mCurrentMovies.clear();
                        mAdapter.notifyDataSetChanged();
                        mActualPage = 1;
                    }
                }
                showProgress();
                forceLoad();
            }

            @Override
            public List<Movie> loadInBackground() {
                List<Movie> movies = new ArrayList<>();
                Cursor cursor = getContentResolver().query(PopMoviesContract.CONTENT_URI,
                        MOVIE_COLUMNS,
                        null,
                        null,
                        null);
                if (cursor.moveToFirst()){
                    do {
                        Movie movie = new Movie();
                        movie.setId(cursor.getInt(COL_MOVIE_MOVIE_ID));
                        movie.setOverview(cursor.getString(COL_MOVIE_OVERVIEW));
                        movie.setReleaseDate(Utility.getFormatDate(cursor.getString(COL_MOVIE_RELEASE_DATE)));
                        movie.setTitle(cursor.getString(COL_MOVIE_ORIGINAL_TITLE));
                        movie.setVoteCount(cursor.getInt(COL_MOVIE_VOTE_COUNT));
                        movie.setVoteAverage(cursor.getDouble(COL_MOVIE_VOTE_AVERAGE));
                        movie.setPosterImage(cursor.getBlob(COL_MOVIE_POSTER));
                        movie.setMarkAsFavorite(true);
                        movies.add(movie);
                        mFavoriteMoviesId.add(movie.getId());
                    } while (cursor.moveToNext());
                }
                cursor.close();
                if (SEARCH_TYPE_FAVORITES.equals(sCurrentSearchType)) {
                    return movies;
                } else {
                    return RequestMovies.requestMovies(getContext(), sCurrentSearchType, mActualPage);
                }
            }
        };
    }

    @Override
    public void onLoadFinished(Loader<List<Movie>> loader, List<Movie> data) {
        isFetching = false;
        if (data == null) {
            if (SEARCH_TYPE_FAVORITES.equals(sCurrentSearchType)) {
                showNoFavoritesFoundMessage();
            } else {
                showMessageError();
            }
        } else {
            showContent();
            mCurrentMovies.addAll(data);
            mAdapter.setmMovieListAndFavorites(mCurrentMovies, mFavoriteMoviesId);
        }
    }

    @Override
    public void onLoaderReset(Loader<List<Movie>> loader) {
    }
}
