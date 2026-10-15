import { redirect } from "next/navigation";

// L'accueil du back-office est le tableau de bord ; la garde de session renvoie vers /login si besoin.
export default function Home() {
  redirect("/dashboard");
}
