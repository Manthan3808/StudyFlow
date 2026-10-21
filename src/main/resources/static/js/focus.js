(() => {
    "use strict";
    const DURATIONS = { focus: 25 * 60, short: 5 * 60, long: 15 * 60 };
    const LABELS = { focus: "TIME TO FOCUS", short: "SHORT BREAK", long: "LONG BREAK" };
    const SUBTITLES = { focus: "You can do this.", short: "Breathe. You've earned it.", long: "Rest and recharge." };
    const $ = (id) => document.getElementById(id);

    let mode = "focus";
    let remaining = DURATIONS.focus;
    let running = false;
    let intervalId = null;
    let endAt = null;
    let completedFocusSessions = 0;
    let toastId = null;

    const display = $("timerDisplay");
    const startPauseButton = $("startPauseButton");
    const resetButton = $("resetButton");
    const skipButton = $("skipButton");
    const timerRing = $("timerRing");
    const timerLabel = $("timerLabel");
    const timerSubtitle = $("timerSubtitle");
    const sessionCount = $("sessionCount");
    const sessionDots = $("sessionDots");
    const sessionHint = $("sessionHint");
    const taskSelect = $("taskSelect");
    const selectedTaskNote = $("selectedTaskNote");
    const toast = $("focusToast");

    function showToast(message) {
        toast.textContent = message;
        toast.classList.add("show");
        if (toastId) window.clearTimeout(toastId);
        toastId = window.setTimeout(() => toast.classList.remove("show"), 3500);
    }

    function formatTime(seconds) {
        const minutes = Math.floor(seconds / 60);
        const secs = seconds % 60;
        return `${String(minutes).padStart(2, "0")}:${String(secs).padStart(2, "0")}`;
    }

    function updateProgress() {
        const fraction = 1 - remaining / DURATIONS[mode];
        timerRing.style.setProperty("--timer-progress", `${Math.max(0, Math.min(1, fraction)) * 360}deg`);
        display.textContent = formatTime(remaining);
        display.setAttribute("aria-label", `${Math.floor(remaining / 60)} minutes ${remaining % 60} seconds`);
        timerLabel.textContent = LABELS[mode];
        timerSubtitle.textContent = SUBTITLES[mode];
        startPauseButton.textContent = running ? "Pause timer" : (remaining === 0 ? "Start again" : (remaining === DURATIONS[mode] ? (mode === "focus" ? "Start focus" : "Start break") : "Resume"));
        document.title = `${formatTime(remaining)} · ${mode === "focus" ? "Focus" : "Break"} | StudyFlow`;
        document.querySelectorAll(".mode-tab").forEach(button => {
            const active = button.dataset.mode === mode;
            button.classList.toggle("active", active);
            button.setAttribute("aria-pressed", String(active));
        });
        sessionCount.textContent = String(completedFocusSessions);
        [...sessionDots.children].forEach((dot, index) => dot.classList.toggle("filled", index < completedFocusSessions % 4));
        sessionHint.textContent = completedFocusSessions > 0
            ? `${4 - (completedFocusSessions % 4 || 4)} more focus session${(4 - (completedFocusSessions % 4 || 4)) === 1 ? "" : "s"} until your next long break.`
            : "Complete 4 focus sessions to earn a long break.";
        if (completedFocusSessions > 0 && completedFocusSessions % 4 === 0) {
            sessionHint.textContent = "Great work! You've completed a set of 4 focus sessions.";
        }
    }

    function stopInterval() {
        if (intervalId !== null) window.clearInterval(intervalId);
        intervalId = null;
        running = false;
        endAt = null;
    }

    function switchMode(nextMode, announce = true) {
        stopInterval();
        mode = nextMode;
        remaining = DURATIONS[mode];
        updateProgress();
        if (announce) showToast(`${LABELS[mode][0]}${LABELS[mode].slice(1).toLowerCase()} selected.`);
    }

    function finishSession() {
        stopInterval();
        remaining = 0;
        updateProgress();
        if (mode === "focus") {
            completedFocusSessions += 1;
            updateProgress();
            if (completedFocusSessions % 4 === 0) {
                showToast("Focus session complete! Four sessions done — time for a long break.");
                switchMode("long", false);
            } else {
                showToast("Focus session complete! Take a short break.");
                switchMode("short", false);
            }
        } else {
            showToast("Break complete. Ready to focus again?");
            switchMode("focus", false);
        }
        startPauseButton.textContent = mode === "focus" ? "Start focus" : "Start break";
    }

    function tick() {
        if (!running || endAt === null) return;
        remaining = Math.max(0, Math.ceil((endAt - Date.now()) / 1000));
        updateProgress();
        if (remaining <= 0) finishSession();
    }

    function startTimer() {
        if (running) {
            stopInterval();
            updateProgress();
            return;
        }
        if (remaining <= 0) remaining = DURATIONS[mode];
        running = true;
        endAt = Date.now() + remaining * 1000;
        intervalId = window.setInterval(tick, 250);
        updateProgress();
    }

    startPauseButton.addEventListener("click", startTimer);
    resetButton.addEventListener("click", () => {
        stopInterval();
        remaining = DURATIONS[mode];
        updateProgress();
        showToast("Timer reset.");
    });
    skipButton.addEventListener("click", () => {
        if (mode === "focus") {
            showToast("Session skipped. Your completed-session count is unchanged.");
            switchMode("focus", false);
        } else {
            switchMode("focus", false);
            showToast("Back to focus mode.");
        }
    });
    document.querySelectorAll(".mode-tab").forEach(button => {
        button.addEventListener("click", () => switchMode(button.dataset.mode));
    });
    taskSelect.addEventListener("change", () => {
        const selected = taskSelect.options[taskSelect.selectedIndex];
        selectedTaskNote.textContent = taskSelect.value
            ? `Focus on: ${selected.textContent}`
            : "Pick a task to keep your session intentional.";
    });

    // Keep countdown accurate if the browser tab sleeps or the laptop wakes up.
    document.addEventListener("visibilitychange", () => {
        if (!document.hidden && running) tick();
    });

    updateProgress();
})();