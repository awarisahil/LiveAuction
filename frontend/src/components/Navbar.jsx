import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth';

export default function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  return (
    <header className="navbar">
      <Link to="/" className="brand">BoltBid</Link>
      <nav className="nav-links">
        <Link to="/">Browse</Link>
        {user && (user.role === 'SELLER' || user.role === 'ADMIN') && <Link to="/sell">Sell an item</Link>}
        {user && <Link to="/profile">Profile</Link>}
        {user?.role === 'ADMIN' && <Link to="/admin" className="nav-admin">Admin</Link>}
      </nav>
      <div className="nav-right">
        {user ? (
          <>
            <span className="who">
              <span className="avatar-sm">{user.avatarEmoji || '\uD83D\uDC64'}</span> {user.name}
            </span>
            <button
              className="ghost small"
              onClick={() => {
                logout();
                navigate('/');
              }}
            >
              Log out
            </button>
          </>
        ) : (
          <>
            <Link to="/login" className="ghost small link-btn">Log in</Link>
            <Link to="/register" className="small">Sign up</Link>
          </>
        )}
      </div>
    </header>
  );
}
