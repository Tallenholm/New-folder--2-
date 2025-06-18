import React from 'react';
import { Light, CircadianPoint, Room, Scene } from './types';

export const APP_NAME = "CircadianGlow";
export const GEMINI_MODEL_TEXT = 'gemini-2.5-flash-preview-04-17'; // Added

export const ICONS = {
  LIGHTBULB_ON: (
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className="w-6 h-6 text-yellow-400">
      <path d="M12 .75a8.25 8.25 0 0 0-4.135 15.39c.686.388 1.115 1.09 1.134 1.863a3 3 0 0 1-.002 1.396h6.006a3 3 0 0 1-.001-1.396c.02-.773.448-1.475 1.134-1.863A8.25 8.25 0 0 0 12 .75Z" />
      <path fillRule="evenodd" d="M9.013 20.25a.75.75 0 0 1 .75-.75h4.474a.75.75 0 0 1 0 1.5H9.763a.75.75 0 0 1-.75-.75Z" clipRule="evenodd" />
      <path d="M12.002 21.75a.75.75 0 0 1-.752-.746c.002-.09.01-.179.022-.262h1.46c.012.083.02.172.022.262a.75.75 0 0 1-.752.746Z" />
    </svg>
  ),
  LIGHTBULB_OFF: (
    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-6 h-6 text-slate-500">
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 .75a8.25 8.25 0 0 0-4.135 15.39c.686.388 1.115 1.09 1.134 1.863a3 3 0 0 1-.002 1.396h6.006a3 3 0 0 1-.001-1.396c.02-.773.448-1.475 1.134-1.863A8.25 8.25 0 0 0 12 .75Zm2.237 19.5a.75.75 0 0 1-.75.75h-3a.75.75 0 0 1 0-1.5h3a.75.75 0 0 1 .75.75ZM12.002 21.75a.75.75 0 0 1-.752-.746c.002-.09.01-.179.022-.262h1.46c.012.083.02.172.022.262a.75.75 0 0 1-.752.746Z" />
    </svg>
  ),
  SUN: (
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className="w-6 h-6 text-yellow-400">
      <path d="M12 2.25a.75.75 0 0 1 .75.75v2.25a.75.75 0 0 1-1.5 0V3a.75.75 0 0 1 .75-.75ZM7.5 12a4.5 4.5 0 1 1 9 0 4.5 4.5 0 0 1-9 0ZM18.894 6.106a.75.75 0 0 1 1.06-1.06l1.591 1.591a.75.75 0 1 1-1.06 1.06l-1.591-1.591ZM21.75 12a.75.75 0 0 1-.75.75h-2.25a.75.75 0 0 1 0-1.5H21a.75.75 0 0 1 .75.75ZM17.836 17.836a.75.75 0 0 1-1.06 1.06l-1.591-1.591a.75.75 0 0 1 1.06-1.06l1.591 1.591ZM12 18a.75.75 0 0 1 .75.75v2.25a.75.75 0 0 1-1.5 0V18.75a.75.75 0 0 1 .75-.75ZM5.106 17.836a.75.75 0 0 1 1.06 1.06l-1.591 1.591a.75.75 0 0 1-1.06-1.06l1.591-1.591ZM4.5 12a.75.75 0 0 1-.75.75H1.5a.75.75 0 0 1 0-1.5h2.25a.75.75 0 0 1 .75.75ZM6.106 6.106a.75.75 0 0 1 1.06-1.06l1.591 1.591a.75.75 0 1 1-1.06 1.06L6.106 6.106Z" />
    </svg>
  ),
  MOON: (
     <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className="w-5 h-5 text-sky-300">
      <path fillRule="evenodd" d="M9.528 1.718a.75.75 0 0 1 .162.819A8.97 8.97 0 0 0 9 6a9 9 0 0 0 9 9 8.97 8.97 0 0 0 3.463-.69.75.75 0 0 1 .981.981A10.503 10.503 0 0 1 18 19.5a10.5 10.5 0 0 1-10.5-10.5A10.503 10.503 0 0 1 9.528 1.718Z" clipRule="evenodd" />
    </svg>
  ),
  SPARKLES: (
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className="w-5 h-5 text-sky-400"> {/* Standardized to w-5 h-5 */}
      <path fillRule="evenodd" d="M9.315 7.584C12.195 3.883 16.695 1.5 21.75 1.5a.75.75 0 0 1 .75.75c0 5.056-2.383 9.555-6.084 12.436A6.75 6.75 0 0 1 9.75 22.5a.75.75 0 0 1-.75-.75v-4.131A15.838 15.838 0 0 1 6.382 15H2.25a.75.75 0 0 1-.75-.75 6.75 6.75 0 0 1 7.815-6.666ZM15 6.75a2.25 2.25 0 1 0 0 4.5 2.25 2.25 0 0 0 0-4.5Z" clipRule="evenodd" />
      <path d="M5.26 17.242a.75.75 0 1 0-.897-1.203 5.243 5.243 0 0 0-2.05 5.022.75.75 0 0 0 .897 1.203A5.243 5.243 0 0 0 5.26 17.242Z" />
    </svg>
  ),
  SEND: (
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className="w-5 h-5">
      <path d="M3.478 2.404a.75.75 0 0 0-.926.941l2.432 7.905H13.5a.75.75 0 0 1 0 1.5H4.984l-2.432 7.905a.75.75 0 0 0 .926.94 60.519 60.519 0 0 0 18.445-8.986.75.75 0 0 0 0-1.218A60.517 60.517 0 0 0 3.478 2.404Z" />
    </svg>
  ),
  SETTINGS: ( 
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className="w-5 h-5">
      <path fillRule="evenodd" d="M11.078 2.25c-.917 0-1.699.663-1.947 1.528A.75.75 0 0 0 8.976 4.5H6.525a.75.75 0 0 0-.612.374l-1.09 1.636a.75.75 0 0 0 .26.97l1.042.782a.75.75 0 0 0 .976-.26l.096-.144a.75.75 0 0 1 .114-.117h1.492a.75.75 0 0 1 .585.393l.818 1.364a.75.75 0 0 0 .976.26l1.042-.782a.75.75 0 0 0-.26-1.442l-.096.064a.75.75 0 0 1-.114.076H11.25a.75.75 0 0 0-.585-.393l-.818-1.364a.75.75 0 0 1-.38-.585H8.976a.75.75 0 0 0-.896-.656l-.066.027A11.954 11.954 0 0 0 7.5 7.5a2.5 2.5 0 1 0 5 0c0-1.006-.333-1.922-.888-2.673a.75.75 0 0 0-.174-.212A3.001 3.001 0 0 0 11.078 2.25ZM12.5 7.5a1 1 0 1 1-2 0 1 1 0 0 1 2 0Z" clipRule="evenodd" />
      <path d="M12.991 1.757a.75.75 0 0 0-1.982 0 12.034 12.034 0 0 0-3.131 2.083 12.012 12.012 0 0 0-1.226 1.504.75.75 0 0 0 .092 1.01l1.042.782a.75.75 0 0 0 .976-.26l.096-.144.004-.006a.75.75 0 0 1 .11-.11h1.492a.75.75 0 0 1 .585.393l.818 1.364a.75.75 0 0 0 .976.26l1.042-.782a.75.75 0 0 0-.26-1.442l-.096.064a.75.75 0 0 1-.114.076H11.25a.75.75 0 0 0-.585-.393l-.818-1.364a.75.75 0 0 1-.38-.585h.206a13.533 13.533 0 0 1 3.518-2.196.75.75 0 0 0 .426-.694Zm5.166 11.965c.164.043.33.078.5.105a.75.75 0 0 0 .708-.707c-.027-.17-.062-.336-.105-.5a13.53 13.53 0 0 0-2.196-3.518.75.75 0 0 0-.694-.426h-.206a.75.75 0 0 1-.585-.38l-1.364-.818a.75.75 0 0 0-.393-.585v-1.492a.75.75 0 0 1-.117-.114l-.144-.096a.75.75 0 0 0-.26-.976l-.782-1.042a.75.75 0 0 0-1.01-.092 12.012 12.012 0 0 0-1.504 1.226 12.034 12.034 0 0 0-2.083 3.131.75.75 0 0 0 0 1.982 12.034 12.034 0 0 0 2.083 3.131 12.012 12.012 0 0 0 1.504 1.226.75.75 0 0 0 1.01.092l.782-1.042a.75.75 0 0 0 .26-.976l.144-.096.006-.004a.75.75 0 0 1 .11-.11v-1.492a.75.75 0 0 1 .393-.585l1.364-.818a.75.75 0 0 0 .585-.38h.206c1.23.616 2.291 1.577 3.009 2.768a.75.75 0 0 0 .374.612l1.636 1.09a.75.75 0 0 0 .97-.26l.782-1.042a.75.75 0 0 0-.26-.976l-.064.096a.75.75 0 0 1-.076.114v.206a.75.75 0 0 0 .393.585l1.364.818a.75.75 0 0 0 .585.38h1.492a.75.75 0 0 1 .117.114l.096.144a.75.75 0 0 0 .976.26l1.042-.782a.75.75 0 0 0-.092-1.01 12.012 12.012 0 0 0-1.226-1.504 12.034 12.034 0 0 0-3.131-2.083.75.75 0 0 0-1.982 0 12.034 12.034 0 0 0-3.131 2.083 12.012 12.012 0 0 0-1.226 1.504.75.75 0 0 0 .092 1.01l1.042.782a.75.75 0 0 0 .976-.26l.096-.144.004-.006a.75.75 0 0 1 .11-.11h.206a13.533 13.533 0 0 1 3.518 2.196Z" />
    </svg>
  ),
  HOME: ( // For Dashboard
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className="w-5 h-5">
      <path d="M11.47 3.841a.75.75 0 0 1 1.06 0l8.69 8.69a.75.75 0 1 0 1.06-1.061l-8.689-8.69a2.25 2.25 0 0 0-3.182 0l-8.69 8.69a.75.75 0 1 0 1.061 1.06l8.69-8.69Z" />
      <path d="m12 5.432 8.159 8.159c.03.03.06.058.091.086v6.198c0 1.035-.84 1.875-1.875 1.875H15a.75.75 0 0 1-.75-.75v-4.5a.75.75 0 0 0-.75-.75h-3a.75.75 0 0 0-.75.75V21a.75.75 0 0 1-.75.75H5.625a1.875 1.875 0 0 1-1.875-1.875v-6.198a2.29 2.29 0 0 0 .091-.086L12 5.432Z" />
    </svg>
  ),
  ROOM_DOOR: ( // For Rooms
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className="w-5 h-5">
      <path fillRule="evenodd" d="M12 1.5A2.25 2.25 0 0 0 9.75 3.75v16.5A2.25 2.25 0 0 0 12 22.5h8.25A2.25 2.25 0 0 0 22.5 20.25V3.75A2.25 2.25 0 0 0 20.25 1.5H12ZM15 9.75a.75.75 0 0 1 .75-.75h1.5a.75.75 0 0 1 0 1.5h-1.5a.75.75 0 0 1-.75-.75Zm.75 3.75a.75.75 0 0 0 0 1.5h1.5a.75.75 0 0 0 0-1.5h-1.5Z" clipRule="evenodd" />
      <path d="M3 3.75A2.25 2.25 0 0 0 .75 6v14.25A2.25 2.25 0 0 0 3 22.5h2.25V3.75H3Z" />
    </svg>
  ),
  WAND: ( // For Scenes
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className="w-5 h-5">
      <path d="M11.04 3.003a.75.75 0 0 1 .913.048l7.502 6a.75.75 0 0 1-.048.913l-2.072 2.59a.75.75 0 0 1-1.01.127L14 11.46V13.5a.75.75 0 0 1-.22.53l-2.25 2.25a.75.75 0 0 1-1.06 0l-2.25-2.25a.75.75 0 0 1-.22-.53v-2.04L5.73 12.7a.75.75 0 0 1-.128-1.01l2.59-2.072a.75.75 0 0 1 .913-.048l2.965 2.372L11.04 3Z" />
      <path fillRule="evenodd" d="M6.09 15.11a.75.75 0 1 0-1.061 1.06l2.122 2.121-2.122 2.122a.75.75 0 1 0 1.06 1.061l2.122-2.122 2.121 2.122a.75.75 0 1 0 1.061-1.06L9.272 18.29l2.121-2.122a.75.75 0 0 0-1.06-1.06L8.212 17.23l-2.121-2.121ZM18.66 8.955a.75.75 0 0 0-1.06-1.06l-2.122 2.121-2.121-2.121a.75.75 0 1 0-1.061 1.06L14.439 10.02l-2.121 2.121a.75.75 0 1 0 1.06 1.06L15.5 11.08l2.122 2.122a.75.75 0 1 0 1.06-1.06l-2.121-2.122 2.121-2.121Z" clipRule="evenodd" />
    </svg>
  ),
  CHEVRON_DOWN: (
    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-4 h-4">
      <path strokeLinecap="round" strokeLinejoin="round" d="m19.5 8.25-7.5 7.5-7.5-7.5" />
    </svg>
  ),
  INFO_CIRCLE: ( // New icon for empty states / info
    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-6 h-6 text-slate-500">
      <path strokeLinecap="round" strokeLinejoin="round" d="M11.25 11.25l.041-.02a.75.75 0 011.063.852l-.708 2.836a.75.75 0 001.063.853l.041-.021M21 12a9 9 0 11-18 0 9 9 0 0118 0zm-9-3.75h.008v.008H12V8.25z" />
    </svg>
  ),
};

export const INITIAL_ROOMS: Room[] = [
  { id: 'room1', name: 'Living Room', icon: React.cloneElement(ICONS.ROOM_DOOR as React.ReactElement<any>, {className: "w-6 h-6 text-sky-400"}) },
  { id: 'room2', name: 'Bedroom', icon: React.cloneElement(ICONS.ROOM_DOOR as React.ReactElement<any>, {className: "w-6 h-6 text-purple-400"}) },
  { id: 'room3', name: 'Office', icon: React.cloneElement(ICONS.ROOM_DOOR as React.ReactElement<any>, {className: "w-6 h-6 text-green-400"}) },
];

export const INITIAL_LIGHTS: Light[] = [
  { id: '1', name: 'Living Room Ceiling', isOn: true, brightness: 80, color: '#FFD700', isCircadianEnabled: true, roomId: 'room1' },
  { id: '2', name: 'Bedroom Lamp', isOn: false, brightness: 50, color: '#FFA500', isCircadianEnabled: false, roomId: 'room2' },
  { id: '3', name: 'Office Desk Light', isOn: true, brightness: 90, color: '#E0FFFF', isCircadianEnabled: false, roomId: 'room3' },
  { id: '4', name: 'Kitchen Downlight', isOn: true, brightness: 70, color: '#FFFACD', isCircadianEnabled: true, roomId: null }, 
];


export const INITIAL_SCENES: Scene[] = [
  {
    id: 'scene1',
    name: 'Movie Night',
    description: 'Dim, warm lighting for a cinematic experience.',
    icon: React.cloneElement(ICONS.WAND as React.ReactElement<any>, {className: "w-5 h-5 text-indigo-400"}),
    lightSettings: [
      { lightId: 'all', settings: { isOn: true, brightness: 30, color: '#FF8C00' } }, 
      { lightId: '1', settings: { brightness: 20 } }, 
    ],
  },
  {
    id: 'scene2',
    name: 'Focus Mode',
    description: 'Bright, cool lighting to enhance concentration.',
    icon: React.cloneElement(ICONS.WAND as React.ReactElement<any>, {className: "w-5 h-5 text-teal-400"}),
    lightSettings: [
      { lightId: 'all', settings: { isOn: true, brightness: 90, color: '#E0FFFF' } }, 
      { lightId: '3', settings: { brightness: 100 } }, 
    ],
  },
  {
    id: 'scene3',
    name: 'Relaxation',
    description: 'Soft, calming blueish tones.',
    icon: React.cloneElement(ICONS.WAND as React.ReactElement<any>, {className: "w-5 h-5 text-blue-400"}),
    lightSettings: [
      { lightId: 'all', settings: { isOn: true, brightness: 40, color: '#ADD8E6' } }, 
    ],
  },
  {
    id: 'scene4',
    name: 'All Off',
    description: 'Turn off all lights.',
    icon: React.cloneElement(ICONS.LIGHTBULB_OFF as React.ReactElement<any>, {className: "w-5 h-5 text-slate-500"}),
    lightSettings: [
      { lightId: 'all', settings: { isOn: false } },
    ],
  },
];


export const CIRCADIAN_DATA: CircadianPoint[] = [
  { time: "00:00", hour: 0, colorTemperature: 2000, brightnessFactor: 0.1, colorHex: "#FF9B51" }, 
  { time: "01:00", hour: 1, colorTemperature: 1900, brightnessFactor: 0.05, colorHex: "#FF8C3E" },
  { time: "02:00", hour: 2, colorTemperature: 1900, brightnessFactor: 0.05, colorHex: "#FF8C3E" },
  { time: "03:00", hour: 3, colorTemperature: 1900, brightnessFactor: 0.05, colorHex: "#FF8C3E" },
  { time: "04:00", hour: 4, colorTemperature: 2000, brightnessFactor: 0.1, colorHex: "#FF9B51" },
  { time: "05:00", hour: 5, colorTemperature: 2200, brightnessFactor: 0.15, colorHex: "#FFAC69" },
  { time: "06:00", hour: 6, colorTemperature: 2500, brightnessFactor: 0.2, colorHex: "#FFB677" }, 
  { time: "07:00", hour: 7, colorTemperature: 2700, brightnessFactor: 0.3, colorHex: "#FFC58A" }, 
  { time: "08:00", hour: 8, colorTemperature: 3000, brightnessFactor: 0.5, colorHex: "#FFD5A3" }, 
  { time: "09:00", hour: 9, colorTemperature: 3500, brightnessFactor: 0.7, colorHex: "#FFE4BE" }, 
  { time: "10:00", hour: 10, colorTemperature: 4000, brightnessFactor: 0.85, colorHex: "#FFF2D9" },
  { time: "11:00", hour: 11, colorTemperature: 4500, brightnessFactor: 0.95, colorHex: "#FFF7E8" },
  { time: "12:00", hour: 12, colorTemperature: 5000, brightnessFactor: 1.0, colorHex: "#FFFFF0" }, 
  { time: "13:00", hour: 13, colorTemperature: 5500, brightnessFactor: 1.0, colorHex: "#F0FFFF" }, 
  { time: "14:00", hour: 14, colorTemperature: 5000, brightnessFactor: 0.9, colorHex: "#FFFFF0" },
  { time: "15:00", hour: 15, colorTemperature: 4500, brightnessFactor: 0.8, colorHex: "#FFF7E8" },
  { time: "16:00", hour: 16, colorTemperature: 4000, brightnessFactor: 0.7, colorHex: "#FFF2D9" }, 
  { time: "17:00", hour: 17, colorTemperature: 3500, brightnessFactor: 0.6, colorHex: "#FFE4BE" }, 
  { time: "18:00", hour: 18, colorTemperature: 3000, brightnessFactor: 0.5, colorHex: "#FFD5A3" }, 
  { time: "19:00", hour: 19, colorTemperature: 2700, brightnessFactor: 0.4, colorHex: "#FFC58A" }, 
  { time: "20:00", hour: 20, colorTemperature: 2500, brightnessFactor: 0.3, colorHex: "#FFB677" }, 
  { time: "21:00", hour: 21, colorTemperature: 2200, brightnessFactor: 0.2, colorHex: "#FFAC69" }, 
  { time: "22:00", hour: 22, colorTemperature: 2000, brightnessFactor: 0.15, colorHex: "#FF9B51" },
  { time: "23:00", hour: 23, colorTemperature: 1900, brightnessFactor: 0.1, colorHex: "#FF8C3E" }, 
];

export const EXAMPLE_AI_PROMPTS = [
  "What's the best light color for studying?",
  "Suggest a lighting scene for a dinner party.",
  "How does light affect sleep?",
  "Explain circadian rhythm in simple terms.",
  "Set my living room to a cozy warm white.",
  "Dim all lights to 30%.",
];
