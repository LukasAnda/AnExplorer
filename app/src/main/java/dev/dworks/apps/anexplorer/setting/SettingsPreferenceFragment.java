package dev.dworks.apps.anexplorer.setting;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.view.LayoutInflater;
import android.view.View;

import com.google.android.material.snackbar.Snackbar;

import androidx.core.content.ContextCompat;
import dev.dworks.apps.anexplorer.DocumentsApplication;
import dev.dworks.apps.anexplorer.R;
import dev.dworks.apps.anexplorer.misc.PinViewHelper;
import dev.dworks.apps.anexplorer.misc.Utils;

import static dev.dworks.apps.anexplorer.setting.SettingsActivity.KEY_ACCENT_COLOR;
import static dev.dworks.apps.anexplorer.setting.SettingsActivity.KEY_ADVANCED_DEVICES;
import static dev.dworks.apps.anexplorer.setting.SettingsActivity.KEY_FILE_HIDDEN;
import static dev.dworks.apps.anexplorer.setting.SettingsActivity.KEY_FILE_SIZE;
import static dev.dworks.apps.anexplorer.setting.SettingsActivity.KEY_FILE_THUMBNAIL;
import static dev.dworks.apps.anexplorer.setting.SettingsActivity.KEY_FOLDER_ANIMATIONS;
import static dev.dworks.apps.anexplorer.setting.SettingsActivity.KEY_FOLDER_SIZE;
import static dev.dworks.apps.anexplorer.setting.SettingsActivity.KEY_PIN_ENABLED;
import static dev.dworks.apps.anexplorer.setting.SettingsActivity.KEY_PIN_SET;
import static dev.dworks.apps.anexplorer.setting.SettingsActivity.KEY_PRIMARY_COLOR;
import static dev.dworks.apps.anexplorer.setting.SettingsActivity.KEY_RECENT_MEDIA;
import static dev.dworks.apps.anexplorer.setting.SettingsActivity.KEY_ROOT_MODE;
import static dev.dworks.apps.anexplorer.setting.SettingsActivity.KEY_THEME_STYLE;

public class SettingsPreferenceFragment extends PreferenceFragment implements Preference
        .OnPreferenceChangeListener, Preference.OnPreferenceClickListener {
    
    private Preference pin_set_preference;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref_settings);
        //General
        findPreference(KEY_FILE_SIZE).setOnPreferenceClickListener(this);
        findPreference(KEY_FOLDER_SIZE).setOnPreferenceClickListener(this);
        findPreference(KEY_FILE_THUMBNAIL).setOnPreferenceClickListener(this);
        findPreference(KEY_FILE_HIDDEN).setOnPreferenceClickListener(this);
        findPreference(KEY_RECENT_MEDIA).setOnPreferenceClickListener(this);
        
        //Theme
        PreferenceCategory preferenceCategory = (PreferenceCategory) findPreference
                ("ThemePreferenceScreen");
        
        Preference preferencePrimaryColor = findPreference(KEY_PRIMARY_COLOR);
        preferencePrimaryColor.setOnPreferenceChangeListener(this);
        preferencePrimaryColor.setOnPreferenceClickListener(this);
        
        findPreference(KEY_ACCENT_COLOR).setOnPreferenceClickListener(this);
        
        Preference preferenceThemeStyle = findPreference(KEY_THEME_STYLE);
        preferenceThemeStyle.setOnPreferenceChangeListener(this);
        preferenceThemeStyle.setOnPreferenceClickListener(this);
        if (DocumentsApplication.isTelevision()) {
            preferenceCategory.removePreference(preferenceThemeStyle);
        }
    
        //Security
        findPreference(KEY_PIN_ENABLED).setOnPreferenceClickListener(this);
        
        pin_set_preference = findPreference(KEY_PIN_SET);
        pin_set_preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
        
            @Override
            public boolean onPreferenceClick(Preference preference) {
                SettingsActivity.logSettingEvent(preference.getKey());
                checkPin();
                return false;
            }
        });
        pin_set_preference.setSummary(SettingsActivity.isPinProtected(getActivity()) ? R.string.pin_set : R.string.pin_disabled);
    
        //Advanced
        findPreference(KEY_ADVANCED_DEVICES).setOnPreferenceClickListener(this);
        findPreference(KEY_ROOT_MODE).setOnPreferenceClickListener(this);
        findPreference(KEY_FOLDER_ANIMATIONS).setOnPreferenceClickListener(this);
        
    }
    
    @Override
    public boolean onPreferenceClick(Preference preference) {
        SettingsActivity.logSettingEvent(preference.getKey());
        return false;
    }
    
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        SettingsActivity.logSettingEvent(preference.getKey());
        ((SettingsActivity) getActivity()).changeActionBarColor(Integer.valueOf(newValue.toString
                ()));
        getActivity().recreate();
        return true;
    }
    
    private void confirmPin(final String pin) {
        final Dialog d = new Dialog(getActivity(), R.style.DocumentsTheme_DailogPIN);
        d.getWindow().setWindowAnimations(R.style.DialogEnterNoAnimation);
        PinViewHelper pinViewHelper = new PinViewHelper((LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE), null, null) {
            public void onEnter(String password) {
                super.onEnter(password);
                if (pin.equals(password)) {
                    SettingsActivity.setPin(getActivity(), password);
                    pin_set_preference.setSummary(SettingsActivity.isPinProtected(getActivity()) ? R.string.pin_set : R.string.pin_disabled);
                    if (password != null && password.length() > 0){
                        showMsg(R.string.pin_set);
                        setInstruction(R.string.pin_set);
                    }
                    d.dismiss();
                    return;
                }
                showError(R.string.pin_mismatch);
                setInstruction(R.string.pin_mismatch);
            }
            
            public void onCancel() {
                super.onCancel();
                d.dismiss();
            }
        };
        View view = pinViewHelper.getView();
        view.findViewById(R.id.logo).setVisibility(View.GONE);
        pinViewHelper.setInstruction(R.string.confirm_pin);
        d.setContentView(view);
        d.show();
    }
    
    private void setPin() {
        final Dialog d = new Dialog(getActivity(), R.style.DocumentsTheme_DailogPIN);
        d.getWindow().setWindowAnimations(R.style.DialogExitNoAnimation);
        View view = new PinViewHelper((LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE), null, null) {
            public void onEnter(String password) {
                super.onEnter(password);
                confirmPin(password);
                d.dismiss();
            }
            
            public void onCancel() {
                super.onCancel();
                d.dismiss();
            }
        }.getView();
        view.findViewById(R.id.logo).setVisibility(View.GONE);
        d.setContentView(view);
        d.show();
    }
    
    private void checkPin() {
        if (SettingsActivity.isPinProtected(getActivity())) {
            final Dialog d = new Dialog(getActivity(), R.style.DocumentsTheme_DailogPIN);
            View view = new PinViewHelper((LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE), null, null) {
                public void onEnter(String password) {
                    super.onEnter(password);
                    if (SettingsActivity.checkPin(getActivity(), password)) {
                        super.onEnter(password);
                        SettingsActivity.setPin(getActivity(), "");
                        pin_set_preference.setSummary(R.string.pin_disabled);
                        showMsg(R.string.pin_disabled);
                        setInstruction(R.string.pin_disabled);
                        d.dismiss();
                        return;
                    }
                    showError(R.string.incorrect_pin);
                    setInstruction(R.string.incorrect_pin);
                }
                
                public void onCancel() {
                    super.onCancel();
                    d.dismiss();
                }
            }.getView();
            view.findViewById(R.id.logo).setVisibility(View.GONE);
            d.setContentView(view);
            d.show();
        }
        else {
            setPin();
        }
    }
    public void showMsg(int msg){
        showToast(msg, ContextCompat.getColor(getActivity(), R.color.button_text_color_default), Snackbar.LENGTH_SHORT);
    }
    
    public void showError(int msg){
        showToast(msg, ContextCompat.getColor(getActivity(), R.color.button_text_color_red), Snackbar.LENGTH_SHORT);
    }
    
    public void showToast(int msg, int actionColor, int duration){
        if(!Utils.isActivityAlive(getActivity())){
            return;
        }
        final Snackbar snackbar = Snackbar.make(getActivity().findViewById(android.R.id.content), msg, duration);
        snackbar.setAction(android.R.string.ok, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                snackbar.dismiss();
            }
        })
                .setActionTextColor(actionColor).show();
    }
}
