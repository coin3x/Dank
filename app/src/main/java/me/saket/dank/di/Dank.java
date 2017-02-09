package me.saket.dank.di;

import android.app.Application;

import com.squareup.moshi.Moshi;
import com.squareup.picasso.Picasso;

import me.saket.dank.data.DankRedditClient;
import me.saket.dank.data.SharedPrefsManager;

public class Dank {

    private static DankAppComponent appComponent;

    public static void initDependencies(Application application, String redditApiSecret) {
        appComponent = DaggerDankAppComponent.builder()
                .dankAppModule(new DankAppModule(application, redditApiSecret))
                .build();
    }

    public static DankRedditClient reddit() {
        return appComponent.dankRedditClient();
    }

    public static SharedPrefsManager sharedPrefs() {
        return appComponent.sharedPrefs();
    }

    public static Moshi moshi() {
        return appComponent.moshi();
    }

    public static Picasso picasso() {
        return appComponent.picasso();
    }

}
