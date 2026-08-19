import "./globals.css";

export const metadata = {
  title: "Backroom Text Game",
  description: "Persistent Backrooms text game"
};

export default function RootLayout({ children }) {
  return (
    <html lang="vi">
      <body>{children}</body>
    </html>
  );
}
