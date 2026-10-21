package com.project.lol.webview.injections

/* CREDIT: Spotilol - Power Save Mode. https://github.com/AldySan */
object PowerSave {
    const val CONTENT = """
        (function(){
            if(window.__splPsInstalled) return;
            window.__splPsInstalled = true;
            // VideoPark owns the single Canvas controller. Never wrap Spotify's
            // timers/rAF: those also drive seeking, playback and connection state.
            window.__splApplyPowerSave = function(on){
                window.__splPowerSavePref = !!on;
                window.__splPsActive = !!on;
                if(window.__splSyncCanvasPower) window.__splSyncCanvasPower();
            };
            window.__splApplyPowerSave(!!window.__splPowerSavePref);
        })();
    """
}
