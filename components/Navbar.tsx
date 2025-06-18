
import React from 'react';
import { APP_NAME, ICONS } from '../constants';

interface NavItemProps {
  label: string;
  icon: React.ReactNode;
  isActive: boolean;
  onClick: () => void;
}

const NavItem: React.FC<NavItemProps> = ({ label, icon, isActive, onClick }) => (
  <button
    onClick={onClick}
    className={`flex items-center px-4 py-2.5 rounded-lg text-sm transition-all duration-200 ease-in-out transform hover:scale-[1.02] focus-visible:ring-2 focus-visible:ring-sky-400 focus-visible:ring-offset-2 focus-visible:ring-offset-slate-800
                ${
                  isActive
                    ? 'bg-sky-500 text-white font-semibold shadow-lg shadow-sky-500/40 ring-1 ring-sky-400/40 ring-inset'
                    : 'text-slate-300 hover:bg-gradient-to-r hover:from-slate-700/60 hover:to-slate-600/70 hover:text-white font-medium' // Subtle gradient shift on hover for inactive
                }`}
    aria-current={isActive ? 'page' : undefined}
  >
    <span className="mr-2.5">{icon}</span>
    {label}
  </button>
);

interface NavbarProps {
  currentPage: string;
  onNavigate: (page: string) => void;
}

const Navbar: React.FC<NavbarProps> = ({ currentPage, onNavigate }) => {
  const navItems = [
    { id: 'dashboard', label: 'Dashboard', icon: ICONS.HOME },
    { id: 'rooms', label: 'Rooms', icon: ICONS.ROOM_DOOR },
    { id: 'scenes', label: 'Scenes', icon: ICONS.WAND },
    { id: 'settings', label: 'Settings', icon: ICONS.SETTINGS },
  ];

  const [isMobileMenuOpen, setIsMobileMenuOpen] = React.useState(false);

  return (
    <nav className="bg-slate-800/80 backdrop-blur-lg sticky top-0 z-50 border-b border-slate-700/60 shadow-sm">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-18">
          <div className="flex items-center group cursor-pointer transition-all duration-200 hover:opacity-90 transform hover:scale-[1.02]" onClick={() => onNavigate('dashboard')}>
            <span className="text-yellow-400 mr-2.5 animate-subtle-icon-glow">{React.cloneElement(ICONS.LIGHTBULB_ON as React.ReactElement<any>, {className: "w-8 h-8"})}</span>
            <span className="font-bold text-xl text-white tracking-tight">{APP_NAME}</span>
          </div>
          <div className="hidden md:flex items-center space-x-1.5">
            {navItems.map((item) => (
              <NavItem
                key={item.id}
                label={item.label}
                icon={React.cloneElement(item.icon as React.ReactElement<any>, {className: "w-5 h-5 opacity-90"})}
                isActive={currentPage === item.id}
                onClick={() => {
                  onNavigate(item.id);
                  setIsMobileMenuOpen(false); 
                }}
              />
            ))}
          </div>
           <div className="md:hidden flex items-center">
             <button
                onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
                className="inline-flex items-center justify-center p-2 rounded-md text-slate-300 hover:text-white hover:bg-slate-700/70 focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-sky-500 transition-all duration-200 ease-in-out"
                aria-controls="mobile-menu"
                aria-expanded={isMobileMenuOpen}
              >
                <span className="sr-only">Open main menu</span>
                <div className="transform transition-transform duration-300 ease-in-out">
                 {isMobileMenuOpen ? (
                   <svg className="block h-6 w-6" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12" /></svg>
                  ) : (
                    <svg className="block h-6 w-6" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4 6h16M4 12h16M4 18h16" /></svg>
                  )}
                </div>
              </button>
          </div>
        </div>
      </div>
      
      <div 
        className={`md:hidden fixed inset-0 z-40 transform transition-all duration-300 ease-in-out ${isMobileMenuOpen ? 'translate-x-0 opacity-100' : '-translate-x-full opacity-0'} bg-slate-800/95 backdrop-blur-xl`} 
        id="mobile-menu-panel"
        style={{ paddingTop: '4.5rem' }} 
      >
          <div className="px-3 pt-2 pb-3 space-y-1.5">
             {navItems.map((item) => {
                const isActive = currentPage === item.id;
                return (
                  <button
                    key={`${item.id}-mobile`}
                    onClick={() => {
                      onNavigate(item.id);
                      setIsMobileMenuOpen(false);
                    }}
                    className={`w-full flex items-center px-4 py-3 rounded-lg text-base transition-all duration-200 ease-in-out focus-visible:ring-2 focus-visible:ring-sky-400 focus-visible:ring-offset-2 focus-visible:ring-offset-slate-800
                                ${
                                  isActive
                                    ? 'bg-sky-500 text-white font-semibold shadow-md shadow-sky-500/30'
                                    : 'text-slate-300 hover:bg-slate-700/70 hover:text-white font-medium'
                                }`}
                    aria-current={isActive ? 'page' : undefined}
                  >
                    {React.cloneElement(item.icon as React.ReactElement<any>, { className: "w-5 h-5 mr-3 opacity-90" })}
                    <span>{item.label}</span>
                  </button>
                );
              })}
          </div>
        </div>
    </nav>
  );
};

export default Navbar;