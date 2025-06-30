package com.etanotifier.service;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import java.util.Map;

public class PlacesHelper {
    private static PlacesClient placesClient;
    private static AutocompleteSessionToken sessionToken;

    public static PlacesClient getPlacesClient(Context context, String apiKey) {
        if (placesClient == null) {
            if (!Places.isInitialized()) {
                Places.initialize(context.getApplicationContext(), apiKey);
            }
            placesClient = Places.createClient(context);
        }
        return placesClient;
    }

    public static AutocompleteSessionToken getSessionToken() {
        if (sessionToken == null) {
            sessionToken = AutocompleteSessionToken.newInstance();
        }
        return sessionToken;
    }

    public static void setupAutocomplete(Context context, PlacesClient client, AutocompleteSessionToken token, AutoCompleteTextView autoCompleteTextView, Map<String, String> suggestionPlaceIdMap) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line);
        autoCompleteTextView.setAdapter(adapter);
        autoCompleteTextView.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() < 5) return;
                FindAutocompletePredictionsRequest request = FindAutocompletePredictionsRequest.builder()
                        .setQuery(s.toString())
                        .setSessionToken(token)
                        .build();
                client.findAutocompletePredictions(request)
                        .addOnSuccessListener(response -> {
                            adapter.clear();
                            suggestionPlaceIdMap.clear();
                            for (AutocompletePrediction prediction : response.getAutocompletePredictions()) {
                                String text = prediction.getFullText(null).toString();
                                String placeId = prediction.getPlaceId();
                                adapter.add(text);
                                suggestionPlaceIdMap.put(text, placeId);
                            }
                            adapter.notifyDataSetChanged();
                        })
                        .addOnFailureListener(exception -> {
                            Toast.makeText(context, "Autocomplete failed: " + exception.getMessage(), Toast.LENGTH_LONG).show();
                        });
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }
}

