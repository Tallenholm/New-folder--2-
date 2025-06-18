
import React, { useState, useCallback, useEffect } from 'react';
import Navbar from './components/Navbar';
import DashboardPage from './components/pages/DashboardPage';
import RoomsPage from './components/pages/RoomsPage';
import ScenesPage from './components/pages/ScenesPage';
import SettingsPage from './components/pages/SettingsPage';
import { Light, Room, Scene } from './types';
import { INITIAL_LIGHTS, INITIAL_ROOMS, INITIAL_SCENES, CIRCADIAN_DATA, APP_NAME, ICONS } from './constants';

type Page = 'dashboard' | 'rooms' | 'scenes' | 'settings';

const getCircadianLightSettings = (hour: number): { color: string; brightness: number } => {
  let lowerPoint = CIRCADIAN_DATA.reduce((prev, curr) => (curr.hour <= hour && curr.hour > prev.hour ? curr : prev), CIRCADIAN_DATA[CIRCADIAN_DATA.length -1]);
  let upperPoint = CIRCADIAN_DATA.find(p => p.hour > hour) || CIRCADIAN_DATA[0];

  if(lowerPoint.hour > hour) { 
      const lastPoint = CIRCADIAN_DATA[CIRCADIAN_DATA.length - 1];
      const firstPoint = CIRCADIAN_DATA[0];
      if (lastPoint && firstPoint) {
         lowerPoint = lastPoint;
         upperPoint = firstPoint;
      }
  }

  const color = lowerPoint.colorHex;
  const hourDiffTotal = (upperPoint.hour - lowerPoint.hour + 24) % 24;
  const hourProgress = (hour - lowerPoint.hour + 24) % 24;
  const progressRatio = hourDiffTotal === 0 ? 1 : hourProgress / hourDiffTotal;
  
  const brightnessFactor = lowerPoint.brightnessFactor + (upperPoint.brightnessFactor - lowerPoint.brightnessFactor) * progressRatio;
  const brightness = Math.max(5, Math.min(100, Math.round(brightnessFactor * 100)));

  return { color, brightness };
};

const App: React.FC = () => {
  const [lights, setLights] = useState<Light[]>(INITIAL_LIGHTS);
  const [rooms, setRooms] = useState<Room[]>(INITIAL_ROOMS);
  const [scenes, setScenes] = useState<Scene[]>(INITIAL_SCENES);
  const [currentHour, setCurrentHour] = useState(new Date().getHours());
  const [currentPage, setCurrentPage] = useState<Page>('dashboard');
  const [activatingSceneId, setActivatingSceneId] = useState<string | null>(null);

  const updateLight = useCallback((id: string, updates: Partial<Light>) => {
    setLights(prevLights =>
      prevLights.map(light =>
        light.id === id ? { ...light, ...updates, isCircadianEnabled: updates.isCircadianEnabled !== undefined ? updates.isCircadianEnabled : light.isCircadianEnabled } : light
      )
    );
  }, []);
  
  const activateScene = useCallback((sceneId: string) => {
    const scene = scenes.find(s => s.id === sceneId);
    if (!scene) return;

    setActivatingSceneId(sceneId);
    setTimeout(() => setActivatingSceneId(null), 700); // Duration of pulse animation

    setLights(prevLights => {
      return prevLights.map(light => {
        let newSettings = { ...light };
        const allSetting = scene.lightSettings.find(s => s.lightId === 'all');
        if (allSetting) {
          newSettings = { ...newSettings, ...allSetting.settings };
          if (allSetting.settings.isCircadianEnabled !== undefined) {
            newSettings.isCircadianEnabled = allSetting.settings.isCircadianEnabled;
          } else if (allSetting.settings.isOn !== undefined || allSetting.settings.color || allSetting.settings.brightness){
             newSettings.isCircadianEnabled = false;
          }
        }
        const specificSetting = scene.lightSettings.find(s => s.lightId === light.id);
        if (specificSetting) {
          newSettings = { ...newSettings, ...specificSetting.settings };
          if (specificSetting.settings.isCircadianEnabled !== undefined) {
            newSettings.isCircadianEnabled = specificSetting.settings.isCircadianEnabled;
          } else if (specificSetting.settings.isOn !== undefined || specificSetting.settings.color || specificSetting.settings.brightness){
             newSettings.isCircadianEnabled = false;
          }
        }
        return newSettings;
      });
    });
    console.log(`Scene "${scene.name}" activated.`);
  }, [scenes]);


  useEffect(() => {
    const timer = setInterval(() => {
      const now = new Date();
      const newHour = now.getHours();
      setCurrentHour(newHour);

      setLights(prevLights =>
        prevLights.map(light => {
          if (light.isCircadianEnabled && light.isOn) {
            const { color, brightness } = getCircadianLightSettings(newHour);
            return { ...light, color, brightness };
          }
          return light;
        })
      );
    }, 60000); 

    const now = new Date();
     setLights(prevLights =>
        prevLights.map(light => {
          if (light.isCircadianEnabled && light.isOn) {
            const { color, brightness } = getCircadianLightSettings(now.getHours());
            return { ...light, color, brightness };
          }
          return light;
        })
      );

    return () => clearInterval(timer);
  }, []); 

  const handleNavigate = (page: string) => {
    setCurrentPage(page as Page);
  };

  const renderPage = () => {
    switch (currentPage) {
      case 'dashboard':
        return <DashboardPage lights={lights} updateLight={updateLight} currentHour={currentHour} />;
      case 'rooms':
        return <RoomsPage rooms={rooms} lights={lights} updateLight={updateLight} />;
      case 'scenes':
        return <ScenesPage scenes={scenes} activateScene={activateScene} activatingSceneId={activatingSceneId} />;
      case 'settings':
        return <SettingsPage />;
      default:
        return <DashboardPage lights={lights} updateLight={updateLight} currentHour={currentHour}/>;
    }
  };

  return (
    <div className="min-h-screen flex flex-col">
      <Navbar currentPage={currentPage} onNavigate={handleNavigate} />
      <main className="flex-grow max-w-7xl mx-auto py-10 px-4 sm:px-6 lg:px-8 w-full">
        {renderPage()}
      </main>
      <footer className="text-center pt-10 pb-8 text-slate-500 text-xs border-t border-slate-700/50 mt-16 shadow-[0_-5px_20px_-8px_rgba(14,165,233,0.1)]">
        <div className="flex items-center justify-center space-x-1.5">
          <span className="animate-subtle-icon-glow opacity-70">
            {React.cloneElement(ICONS.LIGHTBULB_ON as React.ReactElement<any>, { className: "w-3.5 h-3.5 text-slate-500"})}
          </span>
          <p>&copy; {new Date().getFullYear()} {APP_NAME}. AI-enhanced lighting for modern living.</p>
        </div>
        <p className="mt-1">Version 1.0.0</p>
      </footer>
    </div>
  );
};

export default App;