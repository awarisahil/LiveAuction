import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth';

export default function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ name: '', email: '', password: '', role: 'BUYER' });
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    if (form.password.length < 8) {
      setError('Password must be at least 8 characters.');
      return;
    }
    setPending(true);
    try {
      await register({ ...form, email: form.email.trim(), name: form.name.trim() });
      navigate('/');
    } catch (err) {
      setError(err.message);
    } finally {
      setPending(false);
    }
  };

  return (
    <main className="auth-page">
      <form className="auth-card" onSubmit={submit}>
        <h2>Create your account</h2>
        <label>Name</label>
        <input value={form.name} onChange={set('name')} required autoFocus />
        <label>Email</label>
        <input type="email" value={form.email} onChange={set('email')} required />
        <label>Password</label>
        <input type="password" value={form.password} onChange={set('password')} required minLength={8} />
        <label>I want to</label>
        <div className="role-select">
          <button
            type="button"
            className={`chip ${form.role === 'BUYER' ? 'chip-active' : ''}`}
            onClick={() => setForm((f) => ({ ...f, role: 'BUYER' }))}
          >
            Buy items
          </button>
          <button
            type="button"
            className={`chip ${form.role === 'SELLER' ? 'chip-active' : ''}`}
            onClick={() => setForm((f) => ({ ...f, role: 'SELLER' }))}
          >
            Sell items
          </button>
        </div>
        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={pending}>{pending ? 'Creating…' : 'Sign up'}</button>
        <p className="muted">
          Already registered? <Link to="/login">Log in</Link>
        </p>
      </form>
    </main>
  );
}
