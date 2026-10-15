// Zair Auto modification, 2026-10-14: minimal phone navigation. GPL-3.0.
package me.aap.fermata.ui.fragment;

import android.content.Context;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import com.google.android.play.core.splitcompat.SplitCompat;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.VoiceCommand;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.view.FloatingButton;

/**
 * @author Andrey Pavlenko
 */
public abstract class MainActivityFragment extends ActivityFragment {
    @Override
    public me.aap.utils.ui.view.ToolBarView.Mediator getToolBarMediator() {
        return ZairToolBarMediator.instance;
    }


	@Override
	public void onAttach(@NonNull Context context) {
		super.onAttach(context);
		SplitCompat.install(context);
	}

	@Override
	public MainActivityDelegate getActivityDelegate() {
		return (MainActivityDelegate) super.getActivityDelegate();
	}

	@Override
	public NavBarMediator getNavBarMediator() {
		return getActivityDelegate().getNavBarMediator();
	}

	@Override
	public FloatingButton.Mediator getFloatingButtonMediator() {
		return FloatingButtonMediator.instance;
	}

	@CallSuper
	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (hidden) discardSelection();
	}

	@Override
	public boolean onBackPressed() {
		discardSelection();
		return super.onBackPressed();
	}

	public void contributeToNavBarMenu(OverlayMenu.Builder builder) {
	}

	public void discardSelection() {
	}

	public boolean isVideoModeSupported() {
		return false;
	}

	public boolean isVoiceCommandsSupported() {
		return false;
	}

	public boolean startVoiceAssistant() {
		return false;
	}

	public void voiceCommand(VoiceCommand cmd) {
	}
}
