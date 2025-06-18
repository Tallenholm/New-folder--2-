
import React, { useState, useCallback, useRef, useEffect } from 'react';
import { ChatMessage } from '../types';
import { getGeminiSuggestions } from '../services/geminiService';
import { ICONS, EXAMPLE_AI_PROMPTS } from '../constants';
import Button from './common/Button';
import LoadingSpinner from './LoadingSpinner';

const GeminiAssistant: React.FC = () => {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  useEffect(scrollToBottom, [messages]);

  const handleSendMessage = useCallback(async (messageText?: string) => {
    const textToSend = messageText || input.trim();
    if (!textToSend) return;

    const userMessage: ChatMessage = {
      id: Date.now().toString() + '-user',
      sender: 'user',
      text: textToSend,
      timestamp: new Date(),
    };
    setMessages(prev => [...prev, userMessage]);
    if (!messageText) setInput('');
    setIsLoading(true);
    setError(null);

    try {
      const aiResponseText = await getGeminiSuggestions(textToSend);
      const aiMessage: ChatMessage = {
        id: Date.now().toString() + '-ai',
        sender: 'ai',
        text: aiResponseText,
        timestamp: new Date(),
      };
      setMessages(prev => [...prev, aiMessage]);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Failed to get response from AI.';
      setError(errorMessage);
      const aiErrorMessage: ChatMessage = {
        id: Date.now().toString() + '-ai-error',
        sender: 'ai',
        text: `Sorry, I encountered an error. Please try again later. (Details: ${errorMessage})`,
        timestamp: new Date(),
      };
      setMessages(prev => [...prev, aiErrorMessage]);
    } finally {
      setIsLoading(false);
    }
  }, [input]);

  const handleExamplePromptClick = (prompt: string) => {
    handleSendMessage(prompt); 
  };

  // Wrapper for chat messages to apply animation
  const AnimatedChatMessage: React.FC<{ msg: ChatMessage }> = ({ msg }) => {
    const [isVisible, setIsVisible] = useState(false);
    useEffect(() => {
      // Trigger animation shortly after mount
      const timer = setTimeout(() => setIsVisible(true), 50);
      return () => clearTimeout(timer);
    }, []);

    const baseTransition = 'transition-all duration-500 ease-out';
    const initialStyle = msg.sender === 'user' ? 'translate-x-8 opacity-0' : '-translate-x-8 opacity-0';
    const finalStyle = 'translate-x-0 opacity-100';
    
    return (
      <div
        className={`flex ${msg.sender === 'user' ? 'justify-end' : 'justify-start'} ${baseTransition} ${isVisible ? finalStyle : initialStyle}`}
      >
        <div
          className={`max-w-[85%] sm:max-w-[80%] md:max-w-[75%] px-3.5 py-2.5 shadow-lg 
            ${
              msg.sender === 'user'
                ? 'bg-gradient-to-br from-sky-500 to-sky-600 text-white rounded-xl rounded-br-sm'
                : msg.text.startsWith('Sorry, I encountered an error') 
                  ? 'bg-gradient-to-br from-red-600 to-red-700 text-white rounded-xl rounded-bl-sm' 
                  : 'bg-gradient-to-br from-slate-600 to-slate-700 text-slate-200 rounded-xl rounded-bl-sm'
            }`}
        >
          <p className="whitespace-pre-wrap text-sm leading-relaxed">{msg.text}</p>
          <p className={`text-xs mt-1.5 opacity-70 ${msg.sender === 'user' ? 'text-right' : 'text-left'}`}>
            {msg.timestamp.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
          </p>
        </div>
      </div>
    );
  };


  return (
    <div className="bg-slate-800/70 backdrop-blur-lg rounded-xl shadow-xl flex flex-col border border-slate-700/50 max-h-[550px] h-full">
      <div className="p-4 border-b border-slate-700/60 flex items-center shrink-0">
        {React.cloneElement(ICONS.SPARKLES as React.ReactElement<any>, {className: "w-7 h-7 text-sky-400 opacity-90"})}
        <h3 className="text-lg font-semibold text-slate-100 ml-2.5">AI Lighting Assistant</h3>
      </div>
      
      <div className="flex-grow p-4 space-y-4 overflow-y-auto gemini-chat-scrollbar">
        {messages.length === 0 && !isLoading && (
          <div className="text-center text-slate-400 py-6 px-2">
            <p className="mb-5 text-sm">Ask about lighting, circadian rhythms, or get suggestions!</p>
            <div className="space-y-2">
              {EXAMPLE_AI_PROMPTS.slice(0,3).map((prompt, index) => (
                <button 
                  key={index} 
                  onClick={() => handleExamplePromptClick(prompt)}
                  className="w-full text-left justify-start text-xs py-2.5 px-4 rounded-lg
                             bg-slate-700/50 hover:bg-slate-600/80 text-slate-300 hover:text-sky-200
                             transition-all duration-200 ease-in-out transform hover:scale-[1.02] group flex items-center justify-between"
                >
                  <span>{prompt}</span>
                  <span className="opacity-60 group-hover:opacity-100 transform transition-transform duration-200 group-hover:translate-x-0.5">
                    {React.cloneElement(ICONS.CHEVRON_DOWN as React.ReactElement<any>, {className: "w-3 h-3 rotate-[-90deg]"})}
                  </span>
                </button>
              ))}
            </div>
          </div>
        )}
        {messages.map((msg) => (
          <AnimatedChatMessage key={msg.id} msg={msg} />
        ))}
        {isLoading && (
           <div className="flex justify-start">
             <div className="max-w-xs px-4 py-3 rounded-xl shadow-lg bg-gradient-to-br from-slate-600 to-slate-700 text-slate-200">
                <LoadingSpinner size="sm" text="Assistant is thinking"/> {/* Ellipsis added by component */}
             </div>
           </div>
        )}
        <div ref={messagesEndRef} />
      </div>

      <div className="p-3 sm:p-4 border-t border-slate-700/60 shrink-0">
        <form
          onSubmit={(e) => {
            e.preventDefault();
            handleSendMessage();
          }}
          className="flex items-center space-x-2.5"
        >
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Ask for lighting advice..."
            className="flex-grow bg-slate-700/60 border border-slate-600/80 text-slate-100 placeholder-slate-400 rounded-lg py-2.5 px-3.5 focus:outline-none focus:ring-2 focus:ring-sky-500 focus:border-sky-500 text-sm transition-colors"
            disabled={isLoading}
            aria-label="Chat input"
          />
          <Button type="submit" variant="primary" size="md" disabled={isLoading || !input.trim()} isLoading={isLoading} aria-label="Send message">
            {React.cloneElement(ICONS.SEND as React.ReactElement<any>, {className: "w-5 h-5"})}
          </Button>
        </form>
      </div>
    </div>
  );
};

export default GeminiAssistant;