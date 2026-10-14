/* Milestone system removed — GM 1.1.63 restoration (branch restore/gm-1163-remove-milestones).
 *
 * This file is a compatibility stub. index.html still calls
 *   BackroomMilestoneLoading.install({...})
 * at load time (line ~1807) and wires
 *   window.backroomMilestoneStatus / window.backroomMilestoneReady
 * to the returned handlers, so the install() signature and the
 * {sync, status, ready} return shape are preserved as no-ops.
 *
 * Behavior:
 * - The #milestoneLoading panel is permanently hidden.
 * - window.__milestoneBlocked is forced to false and never set true,
 *   so action submit buttons (index.html:1751,1757-1758,1772,2235)
 *   are never blocked by milestones.
 * - No Android milestone bridges are ever called
 *   (prepareMilestone / getMilestoneProviderChoice / chooseMilestoneProvider).
 */
(function(global){
  'use strict';
  global.BackroomMilestoneLoading = { install: function(/* hooks */){
    var panel = document.getElementById('milestoneLoading');
    if (panel) panel.hidden = true;
    // Gameplay is never milestone-blocked. Keep the flag false so the
    // submit-button guards in index.html always pass.
    global.__milestoneBlocked = false;
    function noop(){}
    return {
      sync: function(){
        // Re-assert in case anything else touched the flag.
        global.__milestoneBlocked = false;
        var p = document.getElementById('milestoneLoading');
        if (p) p.hidden = true;
      },
      status: noop,
      ready: noop
    };
  }};
})(window);
