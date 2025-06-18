
import React from 'react';
import { ICONS, APP_NAME } from '../../constants';

const SettingsCard: React.FC<{title: string, icon?: React.ReactNode, children: React.ReactNode}> = ({title, icon, children}) => (
  <section className="bg-slate-800/70 backdrop-blur-lg rounded-xl shadow-xl p-6 md:p-8 border border-slate-700/50 hover:border-sky-500/50 transition-all duration-300 hover:-translate-y-1 group">
    <div className="flex items-center border-b border-slate-700/70 pb-4 mb-5">
      {icon && <span className="mr-3 text-xl text-sky-400 transition-transform duration-300 ease-out group-hover:[&_svg]:rotate-[-3deg]">{icon}</span>}
      <h2 className="text-xl font-semibold text-slate-100">{title}</h2>
    </div>
    {children}
  </section>
);


const SettingsPage: React.FC = () => {
  return (
    <div className="space-y-10">
      <h1 className="text-3xl font-bold tracking-tight text-slate-100 flex items-center mb-10">
        <span className="text-sky-400 mr-3.5 opacity-90">{React.cloneElement(ICONS.SETTINGS as React.ReactElement<any>, {className: "w-9 h-9"})}</span>
        Application Settings
      </h1>
      
      <SettingsCard title="General Configuration" icon={React.cloneElement(ICONS.SETTINGS as React.ReactElement<any>, {className: "w-5 h-5"})}>
        <p className="text-slate-300 leading-relaxed">
          Customize your {APP_NAME} experience. Future options may include:
        </p>
        <ul className="list-disc list-inside text-slate-300 mt-4 space-y-2.5 pl-2">
          <li>Theme preferences (e.g., light/dark mode, contrast)</li>
          <li>Default light transition speeds and effects</li>
          <li>Smart home integration parameters (e.g., Philips Hue, HomeKit)</li>
          <li>Notification preferences for AI suggestions or system alerts</li>
          <li>User account and profile management settings</li>
        </ul>
        <p className="mt-6 text-sm text-slate-400 italic">
          Currently, these settings are illustrative. API key for Gemini is managed securely via environment variables.
        </p>
      </SettingsCard>
       
      <SettingsCard title={`About ${APP_NAME}`} icon={React.cloneElement(ICONS.INFO_CIRCLE as React.ReactElement<any>, {className: "w-5 h-5"})}>
        <p className="text-slate-300 leading-relaxed">
          {APP_NAME} is an intelligent lighting control system designed to harmonize your home's ambiance with natural circadian rhythms. 
          Leveraging cutting-edge AI, it provides personalized recommendations for optimal well-being, productivity, and mood enhancement.
        </p>
        <p className="text-sm text-slate-400 mt-5">Version: 1.0.0 (Enhanced UI Edition)</p>
         <p className="text-xs text-slate-500 mt-1">Last Updated: {new Date().toLocaleDateString()}</p>
      </SettingsCard>

      <SettingsCard title="Support & Feedback" icon={React.cloneElement(ICONS.SPARKLES as React.ReactElement<any>, {className: "w-5 h-5 text-yellow-400"})}>
        <p className="text-slate-300 leading-relaxed">
          Encountering issues, have innovative ideas, or want to share your experience? We're eager to hear from you.
        </p>
        <p className="mt-4">
          <a 
            href="mailto:support@circadianglow.app?subject=Feedback%20for%20CircadianGlow%20App" 
            className="inline-flex items-center text-sky-400 hover:text-sky-300 hover:underline font-medium group"
          >
            Contact Support Team
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" className="w-4 h-4 ml-1.5 transform transition-transform duration-200 group-hover:translate-x-0.5">
              <path fillRule="evenodd" d="M5.22 14.78a.75.75 0 001.06 0l7.22-7.22v5.69a.75.75 0 001.5 0v-7.5a.75.75 0 00-.75-.75h-7.5a.75.75 0 000 1.5h5.69l-7.22 7.22a.75.75 0 000 1.06z" clipRule="evenodd" />
            </svg>
          </a>
        </p>
      </SettingsCard>
    </div>
  );
};

export default SettingsPage;