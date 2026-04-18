// chat.jsx — Gemma Voice chat screen
// Dark mode, voice-only chat. Purple→Cyan gradient accents.

const BG = '#080B14';
const GRAD = 'linear-gradient(90deg, #7C3AED 0%, #06B6D4 100%)';
const GRAD_SOFT = 'linear-gradient(90deg, rgba(124,58,237,0.18) 0%, rgba(6,182,212,0.18) 100%)';

// ─── Icons ────────────────────────────────────────────────
const Icon = {
  back: (s = 22, c = '#fff') => (
    <svg width={s} height={s} viewBox="0 0 24 24" fill="none">
      <path d="M15 6l-6 6 6 6" stroke={c} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
  ),
  mic: (s = 28, c = '#fff') => (
    <svg width={s} height={s} viewBox="0 0 24 24" fill="none">
      <rect x="9" y="3" width="6" height="12" rx="3" fill={c}/>
      <path d="M5.5 11a6.5 6.5 0 0013 0M12 17.5V21M8.5 21h7" stroke={c} strokeWidth="1.8" strokeLinecap="round"/>
    </svg>
  ),
  stop: (s = 24, c = '#fff') => (
    <svg width={s} height={s} viewBox="0 0 24 24">
      <rect x="6" y="6" width="12" height="12" rx="2" fill={c}/>
    </svg>
  ),
  sparkle: (s = 14, c = '#7C3AED') => (
    <svg width={s} height={s} viewBox="0 0 24 24" fill="none">
      <path d="M12 3l1.8 5.4L19 10l-5.2 1.6L12 17l-1.8-5.4L5 10l5.2-1.6L12 3z" fill={c}/>
    </svg>
  ),
  keyboard: (s = 20, c = 'rgba(255,255,255,0.5)') => (
    <svg width={s} height={s} viewBox="0 0 24 24" fill="none">
      <rect x="2.5" y="6" width="19" height="12" rx="2" stroke={c} strokeWidth="1.5"/>
      <path d="M7 11h.01M11 11h.01M15 11h.01M7 14h10" stroke={c} strokeWidth="1.5" strokeLinecap="round"/>
    </svg>
  ),
  more: (s = 22, c = 'rgba(255,255,255,0.6)') => (
    <svg width={s} height={s} viewBox="0 0 24 24" fill="none">
      <circle cx="12" cy="5" r="1.6" fill={c}/>
      <circle cx="12" cy="12" r="1.6" fill={c}/>
      <circle cx="12" cy="19" r="1.6" fill={c}/>
    </svg>
  ),
  check: (s = 12, c = '#22c55e') => (
    <svg width={s} height={s} viewBox="0 0 24 24" fill="none">
      <path d="M5 12l5 5L20 7" stroke={c} strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
  ),
};

// ─── Status bar (matches home screen) ────────────────────
function StatusBar() {
  return (
    <div style={{
      height: 34, display: 'flex', alignItems: 'center',
      justifyContent: 'space-between', padding: '0 20px 0 22px',
      fontSize: 14, color: '#fff', fontWeight: 500,
      fontFamily: 'Manrope, system-ui',
      position: 'relative', flexShrink: 0,
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        <span>20:37</span>
        <div style={{
          display: 'flex', alignItems: 'center', gap: 4,
          background: 'rgba(255,255,255,0.08)', borderRadius: 20,
          padding: '2px 6px', fontSize: 10,
        }}>
          <span>🐝</span><span style={{ opacity: 0.5 }}>·</span><span>🌙</span>
        </div>
        <span style={{ fontSize: 12 }}>☕</span>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        <svg width="14" height="10" viewBox="0 0 14 10"><path d="M7 9.5L.5 3A9.2 9.2 0 0113.5 3L7 9.5z" fill="#fff"/></svg>
        <div style={{ display: 'flex', alignItems: 'flex-end', gap: 1.5 }}>
          {[4,6,8,10].map((h,i) => <div key={i} style={{ width: 2.5, height: h, background: '#fff', borderRadius: 0.5 }}/>)}
        </div>
        <div style={{
          display: 'flex', alignItems: 'center', gap: 2,
          border: '1px solid #fff', borderRadius: 3, padding: '1px 3px 1px 2px',
          fontSize: 9, fontWeight: 600,
        }}>
          <span style={{ color: '#facc15' }}>⚡</span>97
        </div>
      </div>
    </div>
  );
}

// ─── App bar ─────────────────────────────────────────────
function AppBar({ onBack }) {
  return (
    <div style={{
      height: 56, display: 'flex', alignItems: 'center',
      padding: '0 8px', flexShrink: 0, position: 'relative',
      borderBottom: '1px solid rgba(255,255,255,0.04)',
    }}>
      <button onClick={onBack} style={{
        width: 44, height: 44, borderRadius: 22, border: 'none',
        background: 'transparent', display: 'flex', alignItems: 'center',
        justifyContent: 'center', cursor: 'pointer',
      }}>
        {Icon.back(22, 'rgba(255,255,255,0.9)')}
      </button>
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
        <div style={{
          fontSize: 17, fontWeight: 700, letterSpacing: -0.2,
          background: GRAD, WebkitBackgroundClip: 'text',
          WebkitTextFillColor: 'transparent', backgroundClip: 'text',
          fontFamily: 'Manrope, system-ui',
        }}>
          Gemma Voice
        </div>
        <div style={{
          fontSize: 10.5, color: 'rgba(255,255,255,0.45)', marginTop: 1,
          fontFamily: 'JetBrains Mono, monospace', letterSpacing: 0.4,
          display: 'flex', alignItems: 'center', gap: 5,
        }}>
          <span style={{
            width: 5, height: 5, borderRadius: '50%', background: '#22c55e',
            boxShadow: '0 0 6px #22c55e',
          }}/>
          ON-DEVICE · GEMMA 4 E2B
        </div>
      </div>
      <button style={{
        width: 44, height: 44, borderRadius: 22, border: 'none',
        background: 'transparent', display: 'flex', alignItems: 'center',
        justifyContent: 'center', cursor: 'pointer',
      }}>
        {Icon.more()}
      </button>
    </div>
  );
}

// ─── Message bubbles ─────────────────────────────────────
function UserBubble({ text, time }) {
  return (
    <div style={{
      alignSelf: 'flex-end', maxWidth: '78%',
      display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4,
    }}>
      <div style={{
        padding: '11px 15px',
        background: GRAD,
        borderRadius: '20px 20px 6px 20px',
        color: '#fff', fontSize: 15, lineHeight: 1.45,
        fontWeight: 500, letterSpacing: -0.1,
        boxShadow: '0 4px 20px rgba(124,58,237,0.25)',
      }}>
        {text}
      </div>
      <div style={{
        fontSize: 10, color: 'rgba(255,255,255,0.35)',
        fontFamily: 'JetBrains Mono, monospace',
        display: 'flex', gap: 5, alignItems: 'center',
      }}>
        <span>{time}</span>
        <span>·</span>
        <span style={{ color: '#22c55e' }}>voice</span>
      </div>
    </div>
  );
}

function AssistantBubble({ text, time, tokens, tps, streaming }) {
  return (
    <div style={{
      alignSelf: 'flex-start', maxWidth: '82%',
      display: 'flex', flexDirection: 'column', gap: 4,
    }}>
      <div style={{
        display: 'flex', alignItems: 'center', gap: 6,
        paddingLeft: 4, marginBottom: 2,
      }}>
        <div style={{
          width: 18, height: 18, borderRadius: '50%',
          background: GRAD, display: 'flex',
          alignItems: 'center', justifyContent: 'center',
        }}>
          {Icon.sparkle(10, '#fff')}
        </div>
        <span style={{
          fontSize: 11, color: 'rgba(255,255,255,0.55)',
          fontFamily: 'JetBrains Mono, monospace', letterSpacing: 0.3,
          textTransform: 'uppercase', fontWeight: 600,
        }}>Gemma</span>
      </div>
      <div style={{
        padding: '12px 16px',
        background: 'rgba(255,255,255,0.035)',
        border: '1px solid rgba(255,255,255,0.07)',
        borderRadius: '6px 20px 20px 20px',
        color: 'rgba(255,255,255,0.92)',
        fontSize: 15, lineHeight: 1.55,
        fontWeight: 400, letterSpacing: -0.05,
        backdropFilter: 'blur(12px)',
        position: 'relative',
      }}>
        {text}
        {streaming && (
          <span style={{
            display: 'inline-block', width: 8, height: 16,
            background: '#06B6D4', marginLeft: 3, marginBottom: -3,
            animation: 'blink 1s steps(2) infinite',
            verticalAlign: 'middle',
          }}/>
        )}
      </div>
      <div style={{
        fontSize: 10, color: 'rgba(255,255,255,0.35)',
        fontFamily: 'JetBrains Mono, monospace',
        display: 'flex', gap: 6, alignItems: 'center', paddingLeft: 4,
      }}>
        <span>{time}</span>
        <span>·</span>
        <span>{tokens} tok</span>
        <span>·</span>
        <span style={{ color: '#06B6D4' }}>{tps} tok/s</span>
      </div>
    </div>
  );
}

function DateDivider({ label }) {
  return (
    <div style={{
      alignSelf: 'center', display: 'flex', alignItems: 'center',
      gap: 10, margin: '4px 0',
    }}>
      <div style={{ width: 40, height: 1, background: 'rgba(255,255,255,0.08)' }}/>
      <span style={{
        fontSize: 10, color: 'rgba(255,255,255,0.4)',
        fontFamily: 'JetBrains Mono, monospace', letterSpacing: 0.5,
        textTransform: 'uppercase', fontWeight: 600,
      }}>{label}</span>
      <div style={{ width: 40, height: 1, background: 'rgba(255,255,255,0.08)' }}/>
    </div>
  );
}

// ─── Voice control bar ───────────────────────────────────
function Waveform({ state }) {
  // 32 bars, animated based on state
  const bars = Array.from({ length: 36 });
  const active = state === 'listening' || state === 'speaking';
  return (
    <div style={{
      display: 'flex', alignItems: 'center', gap: 3,
      height: 36, flex: 1, justifyContent: 'center',
    }}>
      {bars.map((_, i) => {
        const phase = i * 0.18;
        return (
          <div key={i} style={{
            width: 3, borderRadius: 2,
            background: state === 'listening'
              ? `hsl(${260 + (i / bars.length) * 60}, 90%, 65%)`
              : state === 'speaking'
              ? `hsl(${260 + (i / bars.length) * 60}, 85%, 60%)`
              : 'rgba(255,255,255,0.15)',
            height: active ? undefined : 4,
            animation: active
              ? `wave 1.1s ease-in-out ${phase}s infinite`
              : 'none',
            transformOrigin: 'center',
          }}/>
        );
      })}
    </div>
  );
}

function VoiceBar({ state, setState, transcript }) {
  const labelMap = {
    idle:      { t: 'Tap to speak',         c: 'rgba(255,255,255,0.5)' },
    listening: { t: 'Listening…',           c: '#fff' },
    thinking:  { t: 'Thinking on-device…',  c: '#fff' },
    speaking:  { t: 'Speaking',             c: '#fff' },
  };
  const label = labelMap[state];

  const cycleState = () => {
    const order = ['idle', 'listening', 'thinking', 'speaking'];
    const next = order[(order.indexOf(state) + 1) % order.length];
    setState(next);
  };

  return (
    <div style={{
      flexShrink: 0, padding: '12px 16px 10px',
      background: 'linear-gradient(180deg, rgba(8,11,20,0) 0%, #080B14 40%)',
      position: 'relative',
    }}>
      {/* Live transcript chip — only while listening */}
      {state === 'listening' && transcript && (
        <div style={{
          maxWidth: '90%', margin: '0 auto 10px',
          padding: '8px 14px',
          background: 'rgba(124,58,237,0.12)',
          border: '1px solid rgba(124,58,237,0.3)',
          borderRadius: 16,
          fontSize: 13, color: 'rgba(255,255,255,0.85)',
          textAlign: 'center', lineHeight: 1.4,
          fontStyle: 'italic',
        }}>
          "{transcript}<span style={{ opacity: 0.5 }}>▎</span>"
        </div>
      )}

      {/* Main bar */}
      <div style={{
        display: 'flex', alignItems: 'center', gap: 10,
        padding: '10px 10px 10px 16px',
        background: 'rgba(255,255,255,0.03)',
        border: '1px solid rgba(255,255,255,0.08)',
        borderRadius: 32,
        backdropFilter: 'blur(16px)',
      }}>
        <button style={{
          width: 36, height: 36, borderRadius: 18, border: 'none',
          background: 'rgba(255,255,255,0.05)', display: 'flex',
          alignItems: 'center', justifyContent: 'center', cursor: 'pointer',
          flexShrink: 0,
        }}>
          {Icon.keyboard()}
        </button>
        <Waveform state={state}/>
        <button onClick={cycleState} style={{
          width: 56, height: 56, borderRadius: 28, border: 'none',
          background: state === 'idle'
            ? 'rgba(255,255,255,0.08)'
            : GRAD,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          cursor: 'pointer', flexShrink: 0, position: 'relative',
          boxShadow: state !== 'idle'
            ? '0 0 24px rgba(124,58,237,0.5), 0 0 48px rgba(6,182,212,0.3)'
            : 'none',
          transition: 'all 0.3s ease',
        }}>
          {/* Ripple rings */}
          {state === 'listening' && (
            <>
              <div style={{
                position: 'absolute', inset: -4,
                borderRadius: '50%', border: '2px solid rgba(124,58,237,0.5)',
                animation: 'ripple 1.8s ease-out infinite',
              }}/>
              <div style={{
                position: 'absolute', inset: -4,
                borderRadius: '50%', border: '2px solid rgba(6,182,212,0.5)',
                animation: 'ripple 1.8s ease-out 0.9s infinite',
              }}/>
            </>
          )}
          {state === 'thinking'
            ? <ThinkingDots />
            : state === 'speaking'
            ? Icon.stop(22)
            : Icon.mic(26)}
        </button>
      </div>

      {/* Status label */}
      <div style={{
        textAlign: 'center', marginTop: 8,
        fontSize: 11, color: label.c, fontWeight: 500,
        fontFamily: 'JetBrains Mono, monospace', letterSpacing: 0.4,
        textTransform: 'uppercase',
      }}>
        {label.t}
      </div>
    </div>
  );
}

function ThinkingDots() {
  return (
    <div style={{ display: 'flex', gap: 4 }}>
      {[0, 1, 2].map(i => (
        <div key={i} style={{
          width: 6, height: 6, borderRadius: '50%', background: '#fff',
          animation: `bounce 1.2s ease-in-out ${i * 0.15}s infinite`,
        }}/>
      ))}
    </div>
  );
}

// ─── Ambient glow (matches home screen's subtle depth) ───
function AmbientGlow() {
  return (
    <>
      <div style={{
        position: 'absolute', top: -120, left: -80,
        width: 280, height: 280, borderRadius: '50%',
        background: 'radial-gradient(circle, rgba(124,58,237,0.22) 0%, transparent 60%)',
        pointerEvents: 'none', filter: 'blur(20px)',
      }}/>
      <div style={{
        position: 'absolute', top: 40, right: -100,
        width: 260, height: 260, borderRadius: '50%',
        background: 'radial-gradient(circle, rgba(6,182,212,0.14) 0%, transparent 60%)',
        pointerEvents: 'none', filter: 'blur(20px)',
      }}/>
    </>
  );
}

// ─── Screen ──────────────────────────────────────────────
function ChatScreen({ tweaks }) {
  const [state, setState] = React.useState(tweaks.initialState || 'idle');
  const scrollRef = React.useRef(null);

  React.useEffect(() => { setState(tweaks.initialState); }, [tweaks.initialState]);

  const messages = [
    { kind: 'date', label: 'Today' },
    { kind: 'user', text: 'Hey Gemma, what\'s on my schedule today?', time: '20:31' },
    { kind: 'assistant', text: 'You have three things today: a 10 AM design review, lunch with Priya at 1, and the gym at 7. Want me to read the design review notes?', time: '20:31', tokens: 38, tps: 24 },
    { kind: 'user', text: 'Yeah, just the key points.', time: '20:33' },
    { kind: 'assistant', text: 'The review covers the new onboarding flow. Three open questions: whether to keep the model picker, how to handle first-run permissions, and the copy for the privacy screen.', time: '20:34', tokens: 52, tps: 23 },
    { kind: 'user', text: 'Draft a quick reply saying I\'ll push for keeping the picker.', time: '20:36' },
  ];

  // Add streaming assistant response if state is speaking/thinking
  const liveMessages = [...messages];
  if (state === 'thinking') {
    // user just spoke, waiting
  } else if (state === 'speaking') {
    liveMessages.push({
      kind: 'assistant', streaming: true,
      text: 'Sure — how about: "Agreed on the picker, let\'s keep it visible for power users but collapse it behind Advanced for first-run." Want me to send that',
      time: '20:37', tokens: 26, tps: 25,
    });
  }

  const transcript = state === 'listening'
    ? 'draft a quick reply saying...'
    : '';

  React.useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [state]);

  return (
    <div style={{
      width: '100%', height: '100%', background: BG,
      display: 'flex', flexDirection: 'column',
      position: 'relative', overflow: 'hidden',
      fontFamily: 'Manrope, system-ui, sans-serif',
    }}>
      <AmbientGlow />
      <div style={{ position: 'relative', zIndex: 1, display: 'flex', flexDirection: 'column', height: '100%' }}>
        <StatusBar />
        <AppBar onBack={() => {}} />

        {/* Message history */}
        <div ref={scrollRef} style={{
          flex: 1, overflowY: 'auto', overflowX: 'hidden',
          padding: '16px 16px 8px',
          display: 'flex', flexDirection: 'column', gap: 14,
          scrollbarWidth: 'none',
        }}>
          {liveMessages.map((m, i) => {
            if (m.kind === 'date') return <DateDivider key={i} label={m.label}/>;
            if (m.kind === 'user') return <UserBubble key={i} {...m}/>;
            return <AssistantBubble key={i} {...m}/>;
          })}
          {state === 'thinking' && (
            <div style={{ alignSelf: 'flex-start', display: 'flex', alignItems: 'center', gap: 8, paddingLeft: 4 }}>
              <div style={{
                width: 18, height: 18, borderRadius: '50%',
                background: GRAD, display: 'flex',
                alignItems: 'center', justifyContent: 'center',
                animation: 'pulse-glow 1.4s ease-in-out infinite',
              }}>
                {Icon.sparkle(10, '#fff')}
              </div>
              <div style={{
                padding: '10px 14px',
                background: 'rgba(255,255,255,0.035)',
                border: '1px solid rgba(255,255,255,0.07)',
                borderRadius: '6px 20px 20px 20px',
              }}>
                <ThinkingDots />
              </div>
            </div>
          )}
        </div>

        <VoiceBar state={state} setState={setState} transcript={transcript}/>

        {/* Home indicator */}
        <div style={{
          height: 24, display: 'flex', alignItems: 'center', justifyContent: 'center',
          flexShrink: 0, background: BG,
        }}>
          <div style={{
            width: 108, height: 4, borderRadius: 2,
            background: 'rgba(255,255,255,0.5)',
          }}/>
        </div>
      </div>
    </div>
  );
}

Object.assign(window, { ChatScreen });
