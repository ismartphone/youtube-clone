import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import { SessionProvider } from "@/controllers/session";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Back-office YouTube Clone",
  description: "Administration du catalogue de vidéos",
};

// Composant serveur : il porte les métadonnées, les pages client sont rendues dedans.
export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="fr"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className="min-h-full font-sans">
        <SessionProvider>{children}</SessionProvider>
      </body>
    </html>
  );
}
