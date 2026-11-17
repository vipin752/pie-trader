import React, { useEffect, useState } from 'react';

interface TooltipState {
  title?: string;
  text: string;
  left: number;
  top: number;
  placeAbove: boolean;
}

export const GlobalTooltip: React.FC = () => {
  const [tip, setTip] = useState<TooltipState | null>(null);

  useEffect(() => {
    let activeEl: HTMLElement | null = null;

    const showForElement = (el: HTMLElement) => {
      const text = el.getAttribute('data-tip');
      if (!text) return;
      const title = el.getAttribute('data-tip-title') || undefined;
      const rect = el.getBoundingClientRect();
      const maxWidth = 280;
      const margin = 12;
      const rawLeft = rect.left + rect.width / 2 - maxWidth / 2;
      const left = Math.max(margin, Math.min(rawLeft, window.innerWidth - maxWidth - margin));
      const placeAbove = rect.bottom + 100 > window.innerHeight && rect.top > 90;
      const top = placeAbove ? rect.top - 8 : rect.bottom + 8;

      activeEl = el;
      setTip({ title, text, left, top, placeAbove });
    };

    const handlePointerOver = (e: Event) => {
      const target = e.target as HTMLElement | null;
      const host = target?.closest?.('[data-tip]') as HTMLElement | null;
      if (host) {
        showForElement(host);
      }
    };

    const handlePointerOut = (e: MouseEvent | PointerEvent) => {
      if (!activeEl) return;
      const related = e.relatedTarget as HTMLElement | null;
      if (!related || !activeEl.contains(related)) {
        const nextHost = related?.closest?.('[data-tip]') as HTMLElement | null;
        if (nextHost) {
          showForElement(nextHost);
        } else {
          activeEl = null;
          setTip(null);
        }
      }
    };

    const handleFocusIn = (e: FocusEvent) => {
      const target = e.target as HTMLElement | null;
      const host = target?.closest?.('[data-tip]') as HTMLElement | null;
      if (host) showForElement(host);
    };

    const handleClear = () => {
      activeEl = null;
      setTip(null);
    };

    document.addEventListener('pointerover', handlePointerOver);
    document.addEventListener('pointerout', handlePointerOut);
    document.addEventListener('focusin', handleFocusIn);
    document.addEventListener('focusout', handleClear);
    document.addEventListener('pointerdown', handleClear);
    window.addEventListener('scroll', handleClear, true);

    return () => {
      document.removeEventListener('pointerover', handlePointerOver);
      document.removeEventListener('pointerout', handlePointerOut);
      document.removeEventListener('focusin', handleFocusIn);
      document.removeEventListener('focusout', handleClear);
      document.removeEventListener('pointerdown', handleClear);
      window.removeEventListener('scroll', handleClear, true);
    };
  }, []);

  if (!tip) return null;

  return (
    <div
      role="tooltip"
      style={{
        left: `${tip.left}px`,
        top: `${tip.top}px`,
        transform: tip.placeAbove ? 'translateY(-100%)' : undefined,
      }}
      className="fixed z-[80] pointer-events-none w-[280px] px-3 py-2 rounded-lg border border-slate-700/90 bg-slate-950/95 text-slate-100 shadow-xl backdrop-blur-xs text-[11px] leading-snug"
    >
      {tip.title && (
        <div className="font-semibold text-sky-400 mb-0.5">{tip.title}</div>
      )}
      <div className="text-slate-200">{tip.text}</div>
    </div>
  );
};
