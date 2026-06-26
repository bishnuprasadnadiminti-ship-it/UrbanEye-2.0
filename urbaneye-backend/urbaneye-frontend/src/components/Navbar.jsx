import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { auth } from '../config/firebase';
import { signOut } from 'firebase/auth';
import { useAuth } from '../context/AuthContext';
import { MapPin } from 'lucide-react';

export default function Navbar() {
  const { currentUser } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    try {
      await signOut(auth);
      navigate('/login');
    } catch (error) {
      console.error('Logout failed', error);
    }
  };

  return (
    <nav className="bg-white border-b border-gray-100">
      {/* Saffron, White, Green top strip */}
      <div className="flex h-1.5 w-full">
        <div className="flex-1 bg-[#FF9933]"></div>
        <div className="flex-1 bg-white"></div>
        <div className="flex-1 bg-[#138808]"></div>
      </div>
      
      <div className="w-full px-6 lg:px-8">
        <div className="flex justify-between items-center h-20">
          <div className="flex">
            <Link to="/" className="flex-shrink-0 flex items-center gap-3">
              <div className="bg-[#1e3a8a] p-1.5 rounded-lg flex items-center justify-center">
                 <MapPin className="h-5 w-5 text-white" />
              </div>
              <div className="flex flex-col justify-center">
                <span className="font-bold text-xl text-[#1e3a8a] tracking-tight leading-none mb-0.5">UrbanEye</span>
                <span className="text-[9px] font-bold text-[#FF9933] tracking-[0.2em]">CIVIC INTELLIGENCE</span>
              </div>
            </Link>
          </div>
          <div className="flex items-center space-x-6">
            {currentUser ? (
              <>
                <Link to="/dashboard" className="text-gray-600 hover:text-[#1e3a8a] text-sm font-medium transition-colors">Dashboard</Link>
                <button
                  onClick={handleLogout}
                  className="bg-[#1e3a8a] text-white hover:bg-blue-900 px-5 py-2 rounded-lg text-sm font-medium transition-colors shadow-sm"
                >
                  Logout
                </button>
              </>
            ) : (
              <>
                <Link to="/login" className="text-gray-500 hover:text-[#1e3a8a] text-sm font-medium transition-colors">Sign in</Link>
                <Link to="/register" className="bg-[#1e3a8a] hover:bg-blue-900 text-white px-5 py-2.5 rounded-lg text-sm font-medium transition-all shadow-md active:scale-95">Get Started</Link>
              </>
            )}
          </div>
        </div>                   
      </div>
    </nav>
  );
}
