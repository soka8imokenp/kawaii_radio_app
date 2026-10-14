import { motion } from 'motion/react';
import { useState, type ReactNode } from 'react';
import { player, usePlayer } from '../audio/player';
import { press } from '../lib/feedback';
import { appVersion, checkForUpdate, type UpdateInfo } from '../lib/updater';
import { Icon, Led } from '../components/parts';

/** Two-position hardware switch. */
function Switch2<T extends string>({ value, options, onChange }: { value: T; options: [T, ReactNode][]; onChange: (v: T) => void }) {
  const k = options.findIndex(([v]) => v === value);
  return (
    <div className="rim relative grid grid-cols-2 rounded-[14px] p-[4px]" style={{ width: 132 }}>
      <motion.div className="cap absolute bottom-[4px] top-[4px] rounded-[10px]" style={{ width: 'calc(50% - 4px)' }}
        animate={{ left: k === 0 ? 4 : 'calc(50%)' }} transition={{ type: 'spring', stiffness: 600, damping: 34 }} />
      {options.map(([v, label]) => (
        <button key={v} onClick={() => { if (v !== value) { press(); onChange(v); } }}
          className={`relative z-10 flex h-[34px] items-center justify-center gap-1.5 text-[12px] font-bold uppercase ${v === value ? 'text-[var(--ctl-ink)]' : 'text-[var(--ink-soft)] opacity-70'}`}>
          {label}
        </button>
      ))}
    </div>
  );
}

function Row({ title, sub, children }: { title: string; sub?: string; children: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3 py-3">
      <div className="min-w-0">
        <div className="text-[15px] font-bold text-[var(--ink)]">{title}</div>
        {sub ? <div className="mt-0.5 text-[12.5px] text-[var(--ink-soft)]">{sub}</div> : null}
      </div>
      {children}
    </div>
  );
}

type Check = 'idle' | 'checking' | 'latest' | 'error';
const CHECK_TEXT: Record<Check, string> = {
  idle: 'Yangi versiya bormi, tekshiring',
  checking: 'Tekshirilmoqda…',
  latest: "Sizda eng so'nggi versiya",
  error: "Tekshirib bo'lmadi, internetni tekshiring",
};

export function Profile({ onPremium, onUpdate }: { onPremium: () => void; onUpdate: (u: UpdateInfo) => void }) {
  const s = usePlayer();
  const [check, setCheck] = useState<Check>('idle');
  const runCheck = async () => {
    if (check === 'checking') return;
    press();
    setCheck('checking');
    try {
      const u = await checkForUpdate();
      if (u) { setCheck('idle'); onUpdate(u); } else setCheck('latest');
    } catch {
      setCheck('error');
    }
  };
  return (
    <div className="no-scrollbar flex min-h-0 flex-1 flex-col overflow-y-auto px-4 pb-4 pt-[max(16px,env(safe-area-inset-top))]">
      <header className="flex items-center gap-4">
        <div className="raised flex h-[72px] w-[72px] shrink-0 items-center justify-center rounded-full">
          <img src="/logo.svg" alt="" className="logo-ink h-10 w-10" />
        </div>
        <div className="min-w-0">
          <div className="text-[22px] font-bold leading-none text-[var(--ink)]">Mehmon</div>
          <div className="mt-1.5 font-pixel text-[14px] text-[var(--ink-soft)]">Kawaii Radio tinglovchisi</div>
        </div>
      </header>

      {/* Kawaii Pass status on a small LCD */}
      <div className="bezel mt-5 rounded-[20px] p-[5px]">
        <div className="lcd rounded-[14px] border-[3px] border-[#3a4131] px-4 py-3">
          <div className="flex items-center justify-between font-pixel text-[15px] leading-none">
            <span className="lcd-text">KAWAII PASS</span>
            <span className={s.premium ? 'lcd-text' : 'text-[var(--lcd-ghost)]'}>{s.premium ? 'FAOL ●' : "YO'Q ○"}</span>
          </div>
          <div className="mt-2 grid grid-cols-3 gap-2 font-pixel text-[13px] leading-none">
            <div><div className="text-[24px] lcd-text">{String(s.liked.length).padStart(2, '0')}</div><div className="mt-1 opacity-60">SEVIMLI</div></div>
            <div><div className="text-[24px] lcd-text">{String(s.saved.length).padStart(2, '0')}</div><div className="mt-1 opacity-60">SAQLANGAN</div></div>
            <div><div className="text-[24px] lcd-text">{s.lang.toUpperCase()}</div><div className="mt-1 opacity-60">MATN</div></div>
          </div>
        </div>
      </div>
      {!s.premium ? (
        <button onClick={() => { press(); onPremium(); }} className="mt-3 w-full rounded-[16px] bg-gradient-to-b from-[#ffe08a] to-[#f5b93b] py-3.5 text-[13px] font-bold uppercase tracking-[0.08em] text-[#3a2a05] shadow-[0_6px_14px_rgba(245,185,59,0.3),inset_0_1px_0_rgba(255,255,255,0.7),inset_0_-2px_0_rgba(160,100,10,0.25)]">
          Kawaii Pass olish
        </button>
      ) : null}

      <div className="mt-5 text-[12px] font-bold uppercase tracking-[0.12em] text-[var(--ink-soft)]">Sozlamalar</div>
      <div className="well mt-2 rounded-[18px] px-4">
        <Row title="Qo'shiq matni tili" sub="Matnli qo'shiqlar uchun">
          <Switch2 value={s.lang} onChange={(v) => player.setLang(v)} options={[['uz', "O'zb"], ['jp', '日本']]} />
        </Row>
      </div>

      <div className="mt-5 text-[12px] font-bold uppercase tracking-[0.12em] text-[var(--ink-soft)]">Akkaunt</div>
      <div className="well mt-2 rounded-[18px] px-4">
        <Row title="Kawaii.uz akkaunti" sub="kawaii.uz saytidagi akkauntingizga kiring">
          <button onClick={() => { press(); window.open('https://kawaii.uz/login', '_blank'); }} className="raised shrink-0 rounded-[12px] px-4 py-2 text-[12px] font-bold uppercase">Kirish</button>
        </Row>
        <div className="groove-h" />
        <Row title="Ilova haqida" sub="Kawaii Radio KR-01">
          <span className="font-pixel text-[15px] text-[var(--ink)]">v{appVersion()}</span>
        </Row>
        <div className="groove-h" />
        <Row title="Yangilanishlar" sub={CHECK_TEXT[check]}>
          <button onClick={runCheck} className="raised flex shrink-0 items-center gap-2 rounded-[12px] px-3.5 py-2 text-[12px] font-bold uppercase">
            <Led on={check !== 'idle'} color={check === 'error' ? 'var(--led-red)' : 'var(--led-green)'} pulse={check === 'checking' ? 1.6 : 1} />
            {check === 'checking' ? 'Kuting' : 'Tekshirish'}
          </button>
        </Row>
      </div>
    </div>
  );
}
