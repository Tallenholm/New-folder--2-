
import React from 'react';

interface ToggleSwitchProps {
  id: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  label?: React.ReactNode; // Changed from string to React.ReactNode
  size?: 'sm' | 'md' | 'lg';
}

const ToggleSwitch: React.FC<ToggleSwitchProps> = ({ id, checked, onChange, label, size = 'md' }) => {
  const sizeClasses = {
    sm: { container: 'w-10 h-[22px]', knob: 'w-4 h-4 translate-x-0.5', checkedTranslate: 'translate-x-[1.1rem]' }, // Adjusted for aesthetics
    md: { container: 'w-12 h-6', knob: 'w-[1.125rem] h-[1.125rem] translate-x-0.5', checkedTranslate: 'translate-x-[1.6rem]' }, // Knob slightly larger
    lg: { container: 'w-14 h-7', knob: 'w-5 h-5 translate-x-1', checkedTranslate: 'translate-x-[1.85rem]' },
  };

  const currentSize = sizeClasses[size];

  return (
    <label htmlFor={id} className="flex items-center cursor-pointer select-none group">
      {label && <span className="mr-3 text-sm font-medium text-slate-300 group-hover:text-slate-100 transition-colors">{label}</span>}
      <div className="relative">
        <input
          type="checkbox"
          id={id}
          className="sr-only"
          checked={checked}
          onChange={(e) => onChange(e.target.checked)}
        />
        <div
          className={`block ${currentSize.container} rounded-full transition-colors duration-200 ease-in-out ${
            checked ? 'bg-sky-500 group-hover:bg-sky-400' : 'bg-slate-600 group-hover:bg-slate-500'
          }`}
        ></div>
        <div
          className={`absolute left-0 top-1/2 -translate-y-1/2 ${currentSize.knob} bg-white rounded-full shadow-md transform transition-all duration-200 ease-in-out ${
            checked ? currentSize.checkedTranslate : ''
          }`}
        ></div>
      </div>
    </label>
  );
};

export default ToggleSwitch;